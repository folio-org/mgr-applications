# mgr-applications Bootstrap Caching — Design

**Date:** 2026-06-14
**Status:** Approved design, pending spec review
**Repo:** `mgr-applications`

## 1. Problem & goal

`ModuleBootstrapService` answers the module-bootstrap endpoints with **no caching today** — every
call runs a read-only JPQL query over the `v_module_bootstrap` view plus nested
`InterfaceReferenceEntity` subqueries (`ModuleBootstrapService.java:44,60,69`;
`ModuleBootstrapRepository.java:19-23,33-39`).

The motivating load is a **startup storm**: when many module sidecars start, restart, or scale out,
they each fetch bootstrap from mgr-applications, hammering the DB view with repeated identical
queries. The goal is to cut that DB load and tail latency.

**Goal:** cache bootstrap results per mgr-applications replica, with **precise event-driven
invalidation** so a stale bootstrap is never knowingly served.

**Non-goals:** distributed/shared cache (no Redis/Hazelcast); caching anything beyond the three
bootstrap methods; changing the wire contract of the bootstrap endpoints.

## 2. Locked decisions

| Decision | Choice |
|---|---|
| Cache technology | Spring Cache (`@Cacheable`) backed by **Caffeine 3.1.8**, per-replica local |
| Freshness model | Precise event invalidation (not TTL-driven) |
| Topology | Multi-replica; invalidation broadcast over Kafka |
| Eviction granularity | **Full-flush** of the bootstrap cache on any discovery event (see §5) |
| Egress cache shape | **App-independent intermediate** keyed by `moduleId`; derive ingress/full/egress in memory (Option 2, §4) |
| Writer-node tightening | In-process `afterCommit` evict on the replica that made the change |
| FAR mode | Cache **disabled** (single-replica, Kafka off) — `NoOpCacheManager` |

## 3. Why discovery events are a sufficient, complete invalidation trigger

Verified against the codebase:

- `v_module_bootstrap` is a read-only `@Immutable` view derived from `module.discovery_url` and
  `application_module` (`changelog/changes/v4.0.0/support-ui-module-discovery.xml:49-61`). A module
  only participates as a dependency when `location IS NOT NULL`.
- **Every** mutation of which modules/interfaces are live in that view flows through a discovery
  create/update/delete event:
  - `ModuleDiscoveryService.create/update` set `discovery_url` and publish create/update events.
  - Application/module teardown → `ApplicationService.delete` → `onModuleRemovedFromApplication`
    (reference-counted via `existsByNotIdAndModuleId`, so shared modules are not deleted
    prematurely) → `ModuleDiscoveryService.delete` → `cleanModuleDiscoveryUrl` → publish delete.
- All discovery events are published to the `discovery` destination through the **transactional
  outbox** (`DiscoveryPublisher` → `MessagePublisher` → `TrxOutboxHandler` →
  `TrxOutboxPollingPublisher`), so they are durable and emitted **after** the writing transaction
  commits.

There is no path that changes a live module/interface set without emitting a discovery event, and a
module's own descriptor is immutable per version. Therefore the `{ENV}.discovery` topic is a
complete trigger for bootstrap-cache invalidation.

## 4. Cache model — app-independent intermediate (Option 2)

### 4.1 Insight

The egress query is the full (unscoped) dependency resolution **restricted to** the caller's
`applicationIds`. Across sidecars, the `applicationIds` list is essentially "the tenant's entitled
applications" — largely identical across modules. Putting it in the cache key would embed that
shared list redundantly in every module's entry and inflate entry count to
O(modules × distinct-appId-sets).

**Critical subtlety — the `SELECT DISTINCT` collapse.** `ModuleBootstrapView` is keyed on `@Id id`
only, and `findAllRequiredByModuleId` is `SELECT DISTINCT view`. A module shared across multiple
applications (e.g. the same module version bundled in app `v1.0.0` and app `v1.1.0`) therefore
collapses to **one row with an arbitrary `applicationId`**. The current egress query avoids this by
filtering `applicationId IN :applicationIds` *before* the collapse. So a naive "cache the unscoped
entity result, then filter by `applicationId`" is **wrong** for shared modules — the cached row's
single arbitrary app may not be the in-scope one. The cache must instead keep, per module, its
**full set of applicationIds**, obtained from a **projection query** that returns one row per
`(module, application)` without entity-identity collapse:

```
rows = projection of (id, applicationId, location, systemUserRequired, descriptor)   -- NO DISTINCT collapse,
                                                                                        same WHERE as findAllRequiredByModuleId
group by id -> per module: descriptor / location / systemUserRequired   (module-level: identical across its app-rows)
               + Set<applicationId>
ingress(moduleId) = self module, requiredModules = []
full   (getById)  = dedup( all providers )                       applicationId = a deterministic representative app
egress (appIds)   = dedup( providers whose appId-set ∩ appIds ≠ ∅ )   applicationId = a deterministic in-scope app
                    found = (self appId-set ∩ appIds) ≠ ∅
```

(`descriptor`/`location`/`systemUserRequired` come from the `module` table, so they are identical
across a module's `(module, application)` rows — only `application_id` varies.)

### 4.2 Cached value

A single cache named `module-bootstrap`, keyed by **`moduleId` alone**, holding an immutable
`ModuleBootstrapData` snapshot of the projection rows grouped by module:

- `self`: the requested module's resolved row group, or **null** if the module has no
  `v_module_bootstrap` row (does not exist) — `getData` does **not** throw, so the negative result
  is cacheable and each caller applies its own not-found/`found(false)` rule.
- `providers`: one `ResolvedModule` per dependency module id (a provider of an interface the
  requested module requires/optional — already restricted by the query's WHERE clause).
- each `ResolvedModule` = `{ id, location, systemUserRequired, ModuleDescriptor descriptor (read-only),
  Set<String> applicationIds }`.

The cache stores the **DB-query snapshot** (the expensive part). DTO mapping — `mapper.convert`,
narrowing each provider's `provides` to the requested module's required interfaces, dedup, and
choosing the representative/in-scope `applicationId` — runs per call in `ModuleBootstrapService` over
the immutable snapshot, producing **fresh** DTOs each time. This keeps mapping cheap (no DB), avoids
sharing mutable DTOs across responses, and lets egress stamp an in-scope `applicationId`.

Derivations (pure functions over the snapshot; throw/`found` rules live here, not in `getData`):

- **ingress** → `self == null` ? throw `EntityNotFoundException("Module not found by id: " + moduleId)`
  : `ModuleBootstrap(map(self))` with `requiredModules = []`.
- **full** (`getById`) → `self == null` ? throw the same `EntityNotFoundException`
  : `ModuleBootstrap(map(self), dedup(map(all providers)))`.
- **egress** (`appIds`) → `appIds` empty **or** `self == null` **or** `(self.applicationIds ∩ appIds) == ∅`
  → `EgressBootstrapResult.found(false)`; else
  `found(true).bootstrap(ModuleBootstrap(map(self, inScopeApp), dedup(map(providers whose applicationIds ∩ appIds ≠ ∅, inScopeApp))))`.

`dedup` keeps the highest version per module **name**, matching
`ModuleBootstrapService.deduplicateRequiredModules`. The representative app for `full` and the
in-scope app for `egress` are chosen **deterministically** (e.g. the lexicographically smallest
matching applicationId) so output is stable (today's collapse picks an arbitrary one).

**Avoid Spring self-invocation.** All three public methods must obtain the intermediate through a
proxied `@Cacheable` call. If that `@Cacheable getData(moduleId)` lived on `ModuleBootstrapService`
itself, the internal calls from `getById`/`getIngressBootstrap`/`getEgressBootstrap` would bypass
the cache proxy and caching would silently never trigger. Therefore the `@Cacheable` method lives on
a **dedicated bean** (`ModuleBootstrapDataProvider`) that `ModuleBootstrapService` injects and calls
— an external call, so the proxy is honored. `ModuleBootstrapService` keeps the pure derivation
logic.

### 4.3 Correctness invariants (must be proven by characterization tests before refactor)

- **Shared modules (multi-application) are the reason for the projection.** Because the projection
  preserves each module's full `Set<applicationId>`, egress includes a provider iff its app-set
  intersects `appIds` — reproducing the current pre-collapse SQL filter. A characterization test
  with the *same module id in two applications* and an `appIds` scope containing only the *second*
  app MUST pass (this is the case the naive cached-entity approach gets wrong).
- **Dedup after filter for egress.** Egress dedups among *in-scope* providers; full dedups among
  all providers. Filter-then-dedup over the snapshot reproduces the current SQL behavior (egress
  filters `applicationId IN :applicationIds` *before* dedup).
- **`found(false)` semantics preserved.** Empty `applicationIds`, a non-existent module, or a module
  whose own app-set does not intersect `appIds`, all yield `found(false)` — and never throw — same
  as today. (`getById`/ingress still throw `EntityNotFoundException` for a non-existent module.)
- **Interface narrowing is app-independent** (depends only on the requested module's
  requires/optional), so it is applied per call over the cached descriptor without mutating it.
- **Representative `applicationId`** for `full`/`ingress` is deterministic; today's `SELECT DISTINCT`
  picks an arbitrary one, so no existing test pins a specific value — choosing the smallest matching
  app is a safe, more-stable behavior.

### 4.4 Required refactor: make `buildBootstrap` non-destructive

This is mandatory for Option 2 and also fixes a latent correctness risk flagged during verification:
the current build path **mutates shared state** — `removeNotRequiredInterfaces` calls
`provides.removeIf(...)` on the view's descriptor, and `removeModuleViewById` calls `list.remove(...)`
(`ModuleBootstrapService.java:105-138`). The generated `ModuleBootstrap*` DTOs are mutable.

The refactor:

- Replace the destructive build with read-only derivation: narrow a provider's `provides` by
  **streaming + filtering** the cached `descriptor.getProvides()` into a fresh list per call — never
  `removeIf` on the cached descriptor; never `list.remove` on a cached list.
- Derive ingress/full/egress by producing **fresh** DTO instances each call; never hand back a
  mutable view onto the cached snapshot.
- Cache the **projection snapshot** (`ModuleBootstrapData` of `ResolvedModule` rows), not JPA
  entities — the only shared object is each `ModuleDescriptor` (read-only), avoiding
  detached-entity / Hibernate-proxy hazards and DTO-mutation bleed.

Guard test: a repeated cached call returns data equal to the first call (no cross-call corruption),
and a `getById` immediately followed by an `getEgressBootstrap` for the same module returns
independent, uncorrupted results.

## 5. Invalidation

**Granularity: full-flush.** The `DiscoveryEvent` carries a single `moduleId`, which makes
per-module eviction tempting — but it is **incorrect** here. A discovery change to module *B* must
also invalidate every module *A* whose bootstrap depends on an interface *B* provides (fan-out). So
any discovery event evicts **all entries** of `module-bootstrap`
(`@CacheEvict(cacheNames = "module-bootstrap", allEntries = true)`). This is acceptable because
discovery churn is low relative to the read storm that motivates the cache, and steady-state
scale-out (sidecars starting without any discovery change) keeps the cache warm.

Two invalidation paths, both calling the same evict-all:

1. **Kafka broadcast consumer** — new `@KafkaListener` on `{ENV}.discovery` (e.g. `folio.discovery`;
   env-prefixed, **no tenant segment**, resolved via `KafkaUtils.getEnvTopicName("discovery")`),
   modeled on `EntitlementEventListener` / `EntitlementConsumerConfiguration.java:37-54` with its own
   `ConcurrentKafkaListenerContainerFactory` and `JacksonJsonDeserializer<DiscoveryEvent>`. Two
   critical differences from the entitlement consumer:
   - **Unique consumer group per instance** (broadcast) so *every* replica receives *every* event —
     e.g. `${env}-mgr-applications-bootstrap-cache-<UUID-generated-at-startup>`, overridable via
     `KAFKA_BOOTSTRAP_CACHE_GROUP_ID`. (Same broadcast pattern used by mod-circulation's
     `EventConsumerVerticle`.)
   - **`auto-offset-reset: latest`** — a freshly started replica's cache is cold, so replaying
     discovery history is pointless; it consumes only events from start-up onward.
   - Best-effort error handling (no retry/backoff); evict-all is idempotent, so outbox replay /
     duplicate delivery is harmless.
   This path invalidates all **other** replicas.

2. **In-process `afterCommit` evict** — a bean implementing `ApplicationDiscoveryListener` that, on
   any discovery create/update/delete, registers a `TransactionSynchronization` and evicts the
   local cache in `afterCommit`. The `afterCommit` phase avoids an in-transaction race where a
   concurrent read could repopulate the cache with about-to-be-stale data. This tightens the
   **writing** replica to a ~zero staleness window instead of waiting for the outbox→Kafka round
   trip.

```
discovery change (on some replica)
  ├─ afterCommit ─────────────► local evict-all                 (writer: immediate, post-commit)
  └─ outbox ► {ENV}.discovery ► every replica's per-instance consumer ► evict-all  (cross-replica)
```

**Consistency bound (explicit).** Cross-replica freshness is "evict on every event, sub-second
outbox+Kafka propagation," not a literal zero-staleness window — unavoidable with per-replica local
caches and no distributed lock. The writing replica is effectively zero-window thanks to path (2).

## 6. FAR mode & toggles

FAR mode (`application.far-mode.enabled=true`) disables Kafka/Kong/Okapi/mte via
`@ConditionalOnFarModeDisabled` (`ConditionalOnFarModeDisabled.java:15`) and is **single-replica by
design**. Behavior:

- `CacheManager` bean is the Caffeine manager when caching is enabled **and** FAR mode is off;
  otherwise a `NoOpCacheManager`, which makes `@Cacheable`/`@CacheEvict` no-ops (i.e. today's
  uncached behavior). Losing the cache in a lightweight single-replica registry is negligible.
- The Kafka broadcast consumer + its config are gated by `@ConditionalOnFarModeDisabled` (no Kafka
  in FAR) **and** the cache-enabled toggle.
- The in-process `afterCommit` invalidator is only meaningful when the cache is active; it is a
  no-op against a `NoOpCacheManager`.

## 7. Configuration & dependencies

**`pom.xml`** — add both. The Spring Boot 4.0.6 parent BOM manages the versions of *both*
`spring-boot-starter-cache` and `com.github.ben-manes.caffeine:caffeine`, so omit explicit
`<version>` (the plan verifies the BOM-resolved Caffeine version during the build step):

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<dependency>
  <groupId>com.github.ben-manes.caffeine</groupId>
  <artifactId>caffeine</artifactId>
</dependency>
```

**Cache config** — mirror the canonical `CacheConfiguration` in mgr-tenant-entitlements
(`@EnableCaching` + `CaffeineCacheManager`). New classes (package `org.folio.am.config.cache`,
`org.folio.am.config.properties`):

- `BootstrapCacheProperties` (`@ConfigurationProperties(prefix = "application.bootstrap-cache")`,
  Lombok `@Data`): `enabled` (default `true`), `maxSize` (e.g. `1000`), `ttl` (e.g. `30m`).
- `CacheConfiguration`: provides the Caffeine `CacheManager` (or `NoOpCacheManager` when disabled /
  FAR). Caffeine builder uses `maximumSize` + `expireAfterWrite(ttl)` — the TTL is a **memory bound
  and missed-event backstop only**, not the freshness mechanism.

**`application.yml`** — cache block under `application.bootstrap-cache`; discovery-consumer block
(topic resolved from `spring.kafka.topics.discovery` / `getEnvTopicName`, per-instance group id,
`auto-offset-reset: latest`). A FAR profile / condition leaves the cache disabled.

**Checkstyle:** FOLIO rules apply; method length ≤ 21 lines — keep new config/consumer methods short.

## 8. Touched / new artifacts

- `pom.xml` — `spring-boot-starter-cache`, Caffeine.
- `config/properties/BootstrapCacheProperties.java` — new.
- `config/cache/BootstrapCacheConfiguration.java` — new (`@EnableCaching`, Caffeine/NoOp `CacheManager`,
  cache-name constant `module-bootstrap`).
- `repository/ModuleBootstrapRepository.java` — add a **projection** query
  `findRequiredModuleRows(moduleId)` returning one row per `(module, application)` (the same WHERE
  clause as `findAllRequiredByModuleId`, projected to `id, applicationId, location,
  systemUserRequired, descriptor`, **no `DISTINCT`**). `findAllRequiredByModuleIdInApplications` may
  be deleted once egress derives from the snapshot (confirm no other callers first).
- `domain/entity/ModuleBootstrapRow.java` — new interface projection for those columns.
- `service/ModuleBootstrapData.java` + nested `ResolvedModule` — new immutable snapshot value
  (`self` nullable, `providers` list; each `ResolvedModule = {id, location, systemUserRequired,
  descriptor, Set<applicationId>}`); includes the grouping factory from `List<ModuleBootstrapRow>`.
- `service/ModuleBootstrapDataProvider.java` — new bean holding
  `@Cacheable(cacheNames = "module-bootstrap", key = "#moduleId") ModuleBootstrapData getData(String moduleId)`
  (the only DB-touching, cached call; separate bean so the cache proxy is honored — see §4.2).
- `service/ModuleBootstrapService.java` — refactor: inject `ModuleBootstrapDataProvider` + mapper;
  ingress/full/egress become pure, non-destructive in-memory derivations over the snapshot.
- `integration/kafka/BootstrapCacheInvalidationListener.java` — new `@KafkaListener` → evict-all.
- `integration/kafka/config/BootstrapCacheConsumerConfiguration.java` — new (per-instance group,
  `JacksonJsonDeserializer<DiscoveryEvent>`, `auto-offset-reset=latest`, FAR-gated).
- `service/BootstrapCacheInProcessInvalidator.java` — new `ApplicationDiscoveryListener` doing
  `afterCommit` evict-all (justified: `ModuleDiscoveryService` is `@Transactional` and publishes
  discovery events pre-commit).

## 9. Testing strategy

Conventions: custom `@UnitTest` / `@IntegrationTest` annotations; `BaseIntegrationTest`;
Awaitility; `@Sql`. Note `@Cacheable` is **proxy-based** — cache hit/miss cannot be exercised with a
plain `new ModuleBootstrapService(...)`; cache-behavior tests need a Spring slice
(`@SpringBootTest` of `CacheConfiguration` + service + mocked repository) or the IT.

- **Unit (logic):** keep/extend `ModuleBootstrapServiceTest` for the refactored, non-destructive
  build, dedup, egress `found(false)`, and interface-narrowing equivalence.
- **Non-destructiveness:** assert a second derivation from the same cached intermediate returns data
  equal to the first (no mutation bleed).
- **Cache behavior (Spring slice):** second identical `getById` does not touch the repository;
  distinct `moduleId`s each query once; egress derivations for different `appIds` orderings/subsets
  reuse the single cached intermediate (one repository call).
- **Invalidation IT** (`extends BaseIntegrationTest`): publish a `DiscoveryEvent` to
  `{ENV}.discovery` via `KafkaTemplate`, `await().atMost(...).untilAsserted(...)` that the next call
  re-queries (cache evicted). Run a single IT class per the workspace rule:
  `mvn failsafe:integration-test -Dit.test=**/ModuleBootstrapCacheEvictionIT.java` (add
  `-Dcheckstyle.skip` while iterating).

## 10. Risks & residual items

- **Sub-second cross-replica staleness window** (outbox poll + Kafka propagation) — inherent;
  documented in §5. Writer node is tightened by the in-process `afterCommit` evict.
- **Outbox publishing lag** — if `TrxOutboxPollingPublisher` is backed up, invalidation lags. Out of
  scope here, but worth monitoring outbox backlog as a cache-staleness signal.
- **Cached descriptor size** — entries hold module descriptors (JSONB). Bounded by `maxSize`; entry
  count is O(modules). Tune `maxSize`/`ttl` from metrics.
- **Per-instance group accumulation** — unique consumer groups are ephemeral; ensure they do not
  leave committed offsets lingering on the broker (latest-offset, short-lived groups; acceptable, but
  confirm broker group-expiry settings in the target environment).
- **`found(false)` egress caching** — handled by deriving from the cached intermediate; no separate
  negative-cache entry is stored.
