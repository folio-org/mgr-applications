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

Instead, cache one **app-independent** intermediate per `moduleId` and derive every variant in
memory:

```
findAllRequiredByModuleId(moduleId)        -- app-independent; already what getById runs
   each resolved provider row carries its applicationId
ingress(moduleId) = self module, requiredModules = []
full     (getById) = dedup( all provider rows )
egress  (appIds)   = dedup( provider rows where applicationId ∈ appIds )   (found=false if self row not in scope)
```

### 4.2 Cached value

A single cache named `module-bootstrap`, keyed by **`moduleId` alone**, holding an immutable
`ModuleBootstrapData` value:

- `module`: the self module's bootstrap discovery (for the `module` field of every response).
- `selfApplicationIds`: the application ids the module itself belongs to (for the egress
  `found` check).
- `providers`: the list of dependency-provider rows, each tagged with its `applicationId`, with
  **provided interfaces already narrowed** to the interfaces the requesting module
  requires/optional (this narrowing is `moduleId`-specific but **app-independent**, so it is done
  once at build time).

Derivations are pure functions over `ModuleBootstrapData`:

- **ingress** → `ModuleBootstrap(module, requiredModules=[])`.
- **full** (`getById`) → `ModuleBootstrap(module, deduplicate(providers))`.
- **egress** (`appIds`) → if no self application id ∈ `appIds` → `EgressBootstrapResult.found(false)`;
  else `found(true).bootstrap(ModuleBootstrap(module, deduplicate(providers filtered to applicationId ∈ appIds)))`.

`deduplicate` keeps the highest version per module **name**, matching
`ModuleBootstrapService.deduplicateRequiredModules`.

**Avoid Spring self-invocation.** All three public methods must obtain the intermediate through a
proxied `@Cacheable` call. If that `@Cacheable getData(moduleId)` lived on `ModuleBootstrapService`
itself, the internal calls from `getById`/`getIngressBootstrap`/`getEgressBootstrap` would bypass
the cache proxy and caching would silently never trigger. Therefore the `@Cacheable` method lives on
a **dedicated bean** (`ModuleBootstrapDataProvider`) that `ModuleBootstrapService` injects and calls
— an external call, so the proxy is honored. `ModuleBootstrapService` keeps the pure derivation
logic.

### 4.3 Correctness invariants (verified equivalent to current behavior)

- **Dedup after filter for egress.** Egress dedups among *in-scope* providers; full dedups among
  all providers. Filtering-then-deduplicating in memory reproduces the current SQL behavior exactly
  (the current egress filters by `applicationId IN :applicationIds` *before* dedup).
- **`found(false)` semantics preserved.** Empty `applicationIds`, or a module whose own row is not
  within `appIds`, yields `found(false)` — same as today.
- **Interface narrowing is app-independent**, so it is safe to precompute once in the intermediate.
- A module present in multiple applications appears as multiple provider rows; dedup collapses equal
  versions, matching current output.

### 4.4 Required refactor: make `buildBootstrap` non-destructive

This is mandatory for Option 2 and also fixes a latent correctness risk flagged during verification:
the current build path **mutates shared state** — `removeNotRequiredInterfaces` calls
`provides.removeIf(...)` on the view's descriptor, and `removeModuleViewById` calls `list.remove(...)`
(`ModuleBootstrapService.java:105-138`). The generated `ModuleBootstrap*` DTOs are mutable.

The refactor:

- Build `ModuleBootstrapData` once from the repository result **without mutating** the JPA entities
  or their descriptors (construct new interface lists; never `removeIf` on a cached descriptor's
  `provides`).
- Derive ingress/full/egress by producing **fresh** DTO instances each call; never hand back a
  mutable view onto the cached intermediate.
- Do **not** cache JPA entities directly — `ModuleBootstrapData` is a plain immutable value built
  from the mapper output, avoiding detached-entity / Hibernate-proxy hazards.

Guard test: a repeated cached call returns data equal to the first call (no cross-call corruption).

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

**`pom.xml`** — add (Caffeine version managed at the workspace-standard `3.1.8`):

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<dependency>
  <groupId>com.github.ben-manes.caffeine</groupId>
  <artifactId>caffeine</artifactId>
  <version>${caffeine.version}</version>
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
- `config/cache/CacheConfiguration.java` — new (`@EnableCaching`, Caffeine/NoOp `CacheManager`).
- `service/ModuleBootstrapDataProvider.java` — new bean holding
  `@Cacheable(cacheNames = "module-bootstrap", key = "#moduleId") ModuleBootstrapData getData(String moduleId)`
  (the only DB-touching, cached call; separate bean so the cache proxy is honored — see §4.2).
- `service/ModuleBootstrapService.java` — refactor: inject `ModuleBootstrapDataProvider`; build the
  intermediate non-destructively; ingress/full/egress become pure in-memory derivations.
- `service/ModuleBootstrapData.java` (or `domain/model/`) — new immutable intermediate value.
- `integration/kafka/BootstrapCacheInvalidationListener.java` — new `@KafkaListener` → evict-all.
- `integration/kafka/config/BootstrapCacheConsumerConfiguration.java` — new (per-instance group,
  `JacksonJsonDeserializer<DiscoveryEvent>`, FAR-gated).
- `service/BootstrapCacheInProcessInvalidator.java` — new `ApplicationDiscoveryListener` doing
  `afterCommit` evict-all.

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
