# mgr-applications Bootstrap Caching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a per-replica Caffeine cache to `ModuleBootstrapService` (the module-bootstrap endpoints) with precise, event-driven invalidation, to cut DB load from the sidecar startup storm.

**Architecture:** Cache one immutable, application-independent snapshot per `moduleId` (built from the existing entity query for descriptors plus a new lightweight `(id, applicationId)` projection for the full app-sets); derive ingress/full/egress in memory. Invalidate by full-flush on any discovery event — broadcast across replicas via a per-instance Kafka consumer on `{ENV}.discovery`, plus an in-process `afterCommit` evict that zeroes the writing replica's window. Cache is disabled (NoOp) in FAR mode.

**Tech Stack:** Java 21, Spring Boot 4.0.6, Spring Cache + Caffeine, Spring for Apache Kafka, JPA/Hibernate 6, Liquibase, JUnit 5 + Mockito + Awaitility + Testcontainers.

**Spec:** `docs/superpowers/specs/2026-06-14-bootstrap-caching-design.md`

**Conventions (verified):**
- Tests use custom `@org.folio.test.types.UnitTest` / `@org.folio.test.types.IntegrationTest` (not raw `@Tag`).
- Unit tests: `@ExtendWith(MockitoExtension.class)`, never lenient, stub only what's needed.
- Integration tests extend `org.folio.am.support.base.BaseIntegrationTest` (full `@SpringBootTest`, profile `it`, Testcontainers Postgres + Kafka), use `@Sql`, Awaitility.
- Single IT class run: `mvn failsafe:integration-test -Dit.test=**/<Class>.java` (add `-Dcheckstyle.skip` while iterating). **Force Java 21:** `JAVA_HOME` must point at a JDK 21 (this workspace's `mvn` otherwise defaults to a newer JDK and silently breaks Lombok).
- Checkstyle (FOLIO): method length ≤ 21 lines; keep methods short.
- Commit messages: conventional commits, scope `bootstrap`; end with the `Co-Authored-By` trailer.
- Work happens on the current branch `EUREKA-899` (not `master`).

---

## File Structure

**New production files**
- `src/main/java/org/folio/am/config/properties/BootstrapCacheProperties.java` — `@ConfigurationProperties` (maxSize, ttl).
- `src/main/java/org/folio/am/config/cache/BootstrapCacheConfiguration.java` — `@EnableCaching`; Caffeine `CacheManager` (FAR-off + enabled) or `NoOpCacheManager` fallback; cache-name constant.
- `src/main/java/org/folio/am/domain/entity/ModuleApplicationId.java` — `(getId, getApplicationId)` interface projection.
- `src/main/java/org/folio/am/service/ModuleBootstrapData.java` — immutable snapshot + nested `ResolvedModule` + grouping factory.
- `src/main/java/org/folio/am/service/ModuleBootstrapDataProvider.java` — the single `@Cacheable getData(moduleId)`.
- `src/main/java/org/folio/am/service/BootstrapCacheEvictor.java` — `@CacheEvict(allEntries=true)` shared by both invalidators.
- `src/main/java/org/folio/am/service/BootstrapCacheInProcessInvalidator.java` — `ApplicationDiscoveryListener` doing `afterCommit` evict.
- `src/main/java/org/folio/am/integration/kafka/config/BootstrapCacheConsumerConfiguration.java` — per-instance-group consumer factory.
- `src/main/java/org/folio/am/integration/kafka/BootstrapCacheInvalidationListener.java` — `@KafkaListener` on `{ENV}.discovery` → evict-all.

**Modified production files**
- `pom.xml` — add `spring-boot-starter-cache`, `caffeine`.
- `src/main/java/org/folio/am/repository/ModuleBootstrapRepository.java` — add `findApplicationIdsByModuleId`; remove `findAllRequiredByModuleIdInApplications` (Task 11).
- `src/main/java/org/folio/am/service/ModuleBootstrapService.java` — refactor to derive from the snapshot.
- `src/main/resources/application.yml` — add `application.bootstrap-cache` + `spring.kafka.topics.discovery`.
- `src/test/resources/application-it.yml` — `application.bootstrap-cache.enabled: false` (keep the broad IT suite uncached; cache-specific tests opt in).

**New / modified test files**
- `src/test/java/org/folio/am/config/cache/BootstrapCacheConfigurationTest.java` — new.
- `src/test/java/org/folio/am/service/ModuleBootstrapDataTest.java` — new (pure logic).
- `src/test/java/org/folio/am/service/ModuleBootstrapDataProviderCacheTest.java` — new (cache slice).
- `src/test/java/org/folio/am/service/BootstrapCacheInProcessInvalidatorTest.java` — new.
- `src/test/java/org/folio/am/service/ModuleBootstrapServiceTest.java` — rewrite (mock the data provider).
- `src/test/java/org/folio/am/repository/ModuleBootstrapRepositoryIT.java` — add app-id projection test (+ multi-app fixture); drop the removed-method tests in Task 11.
- `src/test/resources/sql/module-bootstrap-shared-module.sql` — new multi-app fixture.
- `src/test/java/org/folio/am/it/ModuleScopedBootstrapIT.java` — add an end-to-end shared-module egress test.
- `src/test/java/org/folio/am/it/BootstrapCacheInvalidationIT.java` — new (cache on; discovery change refreshes).

---

## Task 1: Add cache dependencies

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add the two dependencies**

In `pom.xml`, inside `<dependencies>`, after the `spring-boot-starter-validation` dependency block (around line 134), add:

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

No `<version>` — both are managed by the Spring Boot 4.0.6 parent BOM.

- [ ] **Step 2: Verify they resolve and the version is sane**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q dependency:get -o 2>/dev/null; JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q dependency:tree -Dincludes=com.github.ben-manes.caffeine:caffeine`
Expected: prints a `com.github.ben-manes.caffeine:caffeine:jar:3.x` line (BOM-managed version). If it does not appear, the BOM does not manage it — pin `<version>3.1.8</version>` (the workspace-standard version) on the caffeine dependency and re-run.

- [ ] **Step 3: Compile**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -DskipTests clean compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add pom.xml
git commit -m "build(bootstrap): add spring-boot-starter-cache and caffeine

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Cache properties + configuration

**Files:**
- Create: `src/main/java/org/folio/am/config/properties/BootstrapCacheProperties.java`
- Create: `src/main/java/org/folio/am/config/cache/BootstrapCacheConfiguration.java`
- Test: `src/test/java/org/folio/am/config/cache/BootstrapCacheConfigurationTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.folio.am.config.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;

@UnitTest
class BootstrapCacheConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
    .withUserConfiguration(BootstrapCacheConfiguration.class);

  @Test
  void cacheManager_isCaffeine_whenEnabledAndFarModeOff() {
    runner.run(context -> {
      assertThat(context).hasSingleBean(CacheManager.class);
      assertThat(context.getBean(CacheManager.class)).isInstanceOf(CaffeineCacheManager.class);
    });
  }

  @Test
  void cacheManager_isNoOp_whenFarModeOn() {
    runner.withPropertyValues("application.far-mode.enabled=true").run(context ->
      assertThat(context.getBean(CacheManager.class)).isInstanceOf(NoOpCacheManager.class));
  }

  @Test
  void cacheManager_isNoOp_whenDisabled() {
    runner.withPropertyValues("application.bootstrap-cache.enabled=false").run(context ->
      assertThat(context.getBean(CacheManager.class)).isInstanceOf(NoOpCacheManager.class));
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -Dcheckstyle.skip test -Dtest=BootstrapCacheConfigurationTest`
Expected: FAIL/compile error — `BootstrapCacheConfiguration` does not exist.

- [ ] **Step 3: Create the properties class**

`src/main/java/org/folio/am/config/properties/BootstrapCacheProperties.java`:

```java
package org.folio.am.config.properties;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "application.bootstrap-cache")
public class BootstrapCacheProperties {

  /**
   * Maximum number of cached module-bootstrap snapshots (Caffeine maximumSize).
   */
  private long maxSize = 1000;

  /**
   * Memory-bound / missed-event backstop expiry (Caffeine expireAfterWrite). Not the freshness
   * mechanism — invalidation is event-driven.
   */
  private Duration ttl = Duration.ofMinutes(30);
}
```

- [ ] **Step 4: Create the configuration class**

`src/main/java/org/folio/am/config/cache/BootstrapCacheConfiguration.java`:

```java
package org.folio.am.config.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.folio.am.config.properties.BootstrapCacheProperties;
import org.folio.am.utils.ConditionalOnFarModeDisabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
@RequiredArgsConstructor
@EnableConfigurationProperties(BootstrapCacheProperties.class)
public class BootstrapCacheConfiguration {

  public static final String MODULE_BOOTSTRAP_CACHE = "module-bootstrap";

  @Bean
  @ConditionalOnFarModeDisabled
  @ConditionalOnProperty(name = "application.bootstrap-cache.enabled", havingValue = "true", matchIfMissing = true)
  public CacheManager bootstrapCacheManager(BootstrapCacheProperties properties) {
    var cacheManager = new CaffeineCacheManager(MODULE_BOOTSTRAP_CACHE);
    cacheManager.setCaffeine(Caffeine.newBuilder()
      .maximumSize(properties.getMaxSize())
      .expireAfterWrite(properties.getTtl()));
    return cacheManager;
  }

  @Bean
  @ConditionalOnMissingBean(CacheManager.class)
  public CacheManager noOpCacheManager() {
    return new NoOpCacheManager();
  }
}
```

The `noOpCacheManager` is declared **after** `bootstrapCacheManager`, so `@ConditionalOnMissingBean` only registers it when the Caffeine bean's conditions (FAR off + cache enabled) did not match.

- [ ] **Step 5: Run the test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -Dcheckstyle.skip test -Dtest=BootstrapCacheConfigurationTest`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/folio/am/config/properties/BootstrapCacheProperties.java \
        src/main/java/org/folio/am/config/cache/BootstrapCacheConfiguration.java \
        src/test/java/org/folio/am/config/cache/BootstrapCacheConfigurationTest.java
git commit -m "feat(bootstrap): add caffeine/no-op cache manager for module-bootstrap

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Configuration wiring (application.yml)

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application-it.yml`

- [ ] **Step 1: Add the discovery consumer topic**

In `src/main/resources/application.yml`, under `spring.kafka.topics` (which currently has only `entitlement`, ~line 43-44), add a `discovery` entry:

```yaml
    topics:
      entitlement: ${KAFKA_ENTITLEMENT_TOPIC:${application.environment}.entitlement}
      discovery: ${KAFKA_DISCOVERY_TOPIC:${application.environment}.discovery}
```

This resolves to e.g. `folio.discovery` — the same env-prefixed topic the outbox publishes to (`getEnvTopicName("discovery")`).

- [ ] **Step 2: Add the bootstrap-cache block**

In `src/main/resources/application.yml`, under the top-level `application:` block (e.g. right after the `http-client:` block, ~line 50), add:

```yaml
  bootstrap-cache:
    enabled: ${BOOTSTRAP_CACHE_ENABLED:true}
    max-size: ${BOOTSTRAP_CACHE_MAX_SIZE:1000}
    ttl: ${BOOTSTRAP_CACHE_TTL:30m}
```

- [ ] **Step 3: Disable the cache in the IT profile**

In `src/test/resources/application-it.yml`, under the top-level `application:` block, add:

```yaml
  bootstrap-cache:
    enabled: false
```

Rationale: the broad IT suite asserts bootstrap correctness with caching disabled (NoOp), so it is unaffected by this change. Cache-specific tests (Tasks 6 and 12) opt in explicitly.

- [ ] **Step 4: Verify the context still boots**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -DskipTests clean compile`
Expected: BUILD SUCCESS (YAML is not compiled, but this confirms nothing else broke). Full context boot is verified by the ITs later.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/application.yml src/test/resources/application-it.yml
git commit -m "feat(bootstrap): add bootstrap-cache config and discovery consumer topic

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: App-id projection query (+ multi-app repository test)

**Files:**
- Create: `src/main/java/org/folio/am/domain/entity/ModuleApplicationId.java`
- Modify: `src/main/java/org/folio/am/repository/ModuleBootstrapRepository.java`
- Create: `src/test/resources/sql/module-bootstrap-shared-module.sql`
- Modify: `src/test/java/org/folio/am/repository/ModuleBootstrapRepositoryIT.java`

- [ ] **Step 1: Create the multi-app fixture**

`src/test/resources/sql/module-bootstrap-shared-module.sql` — the shared provider `mod-bar` lives in **two** applications:

```sql
INSERT INTO application(id, name, version, application_descriptor) VALUES
  ('app-a-1.0.0', 'app-a', '1.0.0', '{"id": "app-a-1.0.0"}'),
  ('app-b-1.0.0', 'app-b', '1.0.0', '{"id": "app-b-1.0.0"}');

INSERT INTO module(id, name, version, discovery_url, descriptor, type) VALUES
  ('mod-foo-1.0.0', 'mod-foo', '1.0.0', 'http://mod-foo:8080', '{}', 'BACKEND'),
  ('mod-bar-1.0.0', 'mod-bar', '1.0.0', 'http://mod-bar:8080', '{}', 'BACKEND');

INSERT INTO application_module(application_id, module_id) VALUES
  ('app-a-1.0.0', 'mod-foo-1.0.0'),
  ('app-a-1.0.0', 'mod-bar-1.0.0'),
  ('app-b-1.0.0', 'mod-bar-1.0.0');

INSERT INTO module_interface_reference(module_id, id, version, type) VALUES
  ('mod-foo-1.0.0', 'bar-int', '1.0', 'REQUIRES'),
  ('mod-bar-1.0.0', 'bar-int', '1.0', 'PROVIDES');
```

- [ ] **Step 2: Write the failing repository test**

Add to `src/test/java/org/folio/am/repository/ModuleBootstrapRepositoryIT.java` (new test method; keep the existing class annotations/fields):

```java
  @Test
  @Sql(scripts = "classpath:/sql/module-bootstrap-shared-module.sql", executionPhase = BEFORE_TEST_METHOD)
  void findApplicationIdsByModuleId_returnsAllAppRows_forSharedModule() {
    var rows = repository.findApplicationIdsByModuleId("mod-foo-1.0.0");

    // mod-foo in app-a; mod-bar shared across app-a and app-b -> 3 (id, applicationId) rows total
    assertThat(rows).hasSize(3);
    assertThat(rows).filteredOn(r -> r.getId().equals("mod-bar-1.0.0"))
      .extracting(ModuleApplicationId::getApplicationId)
      .containsExactlyInAnyOrder("app-a-1.0.0", "app-b-1.0.0");
    assertThat(rows).filteredOn(r -> r.getId().equals("mod-foo-1.0.0"))
      .extracting(ModuleApplicationId::getApplicationId)
      .containsExactly("app-a-1.0.0");
  }
```

Add imports: `org.folio.am.domain.entity.ModuleApplicationId`. (`assertThat`, `@Sql`, `BEFORE_TEST_METHOD` are already imported in this class.)

- [ ] **Step 3: Run it to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -Dcheckstyle.skip failsafe:integration-test -Dit.test=**/ModuleBootstrapRepositoryIT.java`
Expected: compile error — `ModuleApplicationId` and `findApplicationIdsByModuleId` do not exist.

- [ ] **Step 4: Create the projection interface**

`src/main/java/org/folio/am/domain/entity/ModuleApplicationId.java`:

```java
package org.folio.am.domain.entity;

/**
 * Projection of a single {@code v_module_bootstrap} row, exposing the module id and the application
 * it belongs to. Unlike the {@link ModuleBootstrapView} entity (keyed on id), this projection is NOT
 * collapsed by {@code SELECT DISTINCT}, so a module shared across applications yields one row per
 * application — the full application-set needed for correct egress scoping.
 */
public interface ModuleApplicationId {

  String getId();

  String getApplicationId();
}
```

- [ ] **Step 5: Add the query**

In `src/main/java/org/folio/am/repository/ModuleBootstrapRepository.java`, add (import `org.folio.am.domain.entity.ModuleApplicationId`):

```java
  /**
   * Returns one (moduleId, applicationId) row per (module, application) pair for the module and all
   * its required-interface providers — same selection as {@link #findAllRequiredByModuleId(String)},
   * but WITHOUT the entity {@code DISTINCT} collapse, so shared modules expose their full app-set.
   *
   * @param moduleId the module identifier
   * @return list of (id, applicationId) projections
   */
  @Query(value = "SELECT view.id AS id, view.applicationId AS applicationId FROM ModuleBootstrapView view "
    + "WHERE (view.id = :moduleId OR view.id IN "
    + "(SELECT p.moduleId FROM InterfaceReferenceEntity p WHERE p.type = 'PROVIDES' AND "
    + "p.id IN (SELECT r.id FROM InterfaceReferenceEntity r WHERE r.moduleId = :moduleId AND "
    + "(r.type = 'REQUIRES' OR r.type = 'OPTIONAL')))) and (view.location is not null OR view.id = :moduleId)")
  List<ModuleApplicationId> findApplicationIdsByModuleId(@Param("moduleId") String moduleId);
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -Dcheckstyle.skip failsafe:integration-test -Dit.test=**/ModuleBootstrapRepositoryIT.java`
Expected: PASS (all existing tests + the new one).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/folio/am/domain/entity/ModuleApplicationId.java \
        src/main/java/org/folio/am/repository/ModuleBootstrapRepository.java \
        src/test/resources/sql/module-bootstrap-shared-module.sql \
        src/test/java/org/folio/am/repository/ModuleBootstrapRepositoryIT.java
git commit -m "feat(bootstrap): add per-application id projection for module bootstrap

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: ModuleBootstrapData snapshot + grouping

**Files:**
- Create: `src/main/java/org/folio/am/service/ModuleBootstrapData.java`
- Test: `src/test/java/org/folio/am/service/ModuleBootstrapDataTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.folio.am.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.support.TestValues.moduleBootstrapView;

import java.util.List;
import org.folio.am.domain.entity.ModuleApplicationId;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ModuleBootstrapDataTest {

  private static ModuleApplicationId appId(String id, String applicationId) {
    return new ModuleApplicationId() {
      @Override public String getId() {
        return id;
      }

      @Override public String getApplicationId() {
        return applicationId;
      }
    };
  }

  @Test
  void from_groupsSelfAndProviders_withFullApplicationSets() {
    var self = moduleBootstrapView("mod-foo-1.0.0", "foo-int");
    var provider = moduleBootstrapView("mod-bar-1.0.0", "bar-int");
    var appIdRows = List.of(
      appId("mod-foo-1.0.0", "app-a-1.0.0"),
      appId("mod-bar-1.0.0", "app-a-1.0.0"),
      appId("mod-bar-1.0.0", "app-b-1.0.0")); // shared provider

    var data = ModuleBootstrapData.from("mod-foo-1.0.0", List.of(self, provider), appIdRows);

    assertThat(data.self()).isNotNull();
    assertThat(data.self().id()).isEqualTo("mod-foo-1.0.0");
    assertThat(data.self().applicationIds()).containsExactly("app-a-1.0.0");
    assertThat(data.providers()).singleElement()
      .satisfies(p -> {
        assertThat(p.id()).isEqualTo("mod-bar-1.0.0");
        assertThat(p.applicationIds()).containsExactlyInAnyOrder("app-a-1.0.0", "app-b-1.0.0");
      });
  }

  @Test
  void from_returnsNullSelf_whenModuleAbsent() {
    var provider = moduleBootstrapView("mod-bar-1.0.0", "bar-int");
    var data = ModuleBootstrapData.from("mod-foo-1.0.0", List.of(provider),
      List.of(appId("mod-bar-1.0.0", "app-a-1.0.0")));

    assertThat(data.self()).isNull();
    assertThat(data.providers()).hasSize(1);
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -Dcheckstyle.skip test -Dtest=ModuleBootstrapDataTest`
Expected: FAIL/compile error — `ModuleBootstrapData` does not exist.

- [ ] **Step 3: Create the snapshot type**

`src/main/java/org/folio/am/service/ModuleBootstrapData.java`:

```java
package org.folio.am.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.am.domain.entity.ModuleApplicationId;
import org.folio.am.domain.entity.ModuleBootstrapView;
import org.folio.common.domain.model.ModuleDescriptor;

/**
 * Immutable, application-independent snapshot of a module's bootstrap resolution. Cached by module
 * id; ingress/full/egress are derived from it in memory. {@code self} is {@code null} when the module
 * has no {@code v_module_bootstrap} row (does not exist).
 */
public record ModuleBootstrapData(ResolvedModule self, List<ResolvedModule> providers) {

  /**
   * A resolved module row group: module-level fields (identical across the module's applications)
   * plus the full set of applications the module belongs to.
   */
  public record ResolvedModule(String id, String location, boolean systemUserRequired,
                               ModuleDescriptor descriptor, Set<String> applicationIds) {}

  /**
   * Builds the snapshot from the (distinct) entity rows (descriptors) and the (un-collapsed)
   * (id, applicationId) projection rows (application sets).
   */
  public static ModuleBootstrapData from(String moduleId, List<ModuleBootstrapView> rows,
    List<ModuleApplicationId> appIdRows) {
    Map<String, Set<String>> appIdsById = appIdRows.stream().collect(groupingBy(
      ModuleApplicationId::getId, mapping(ModuleApplicationId::getApplicationId, toUnmodifiableSet())));

    ResolvedModule self = null;
    var providers = new ArrayList<ResolvedModule>();
    for (var row : rows) {
      var resolved = new ResolvedModule(row.getId(), row.getLocation(), row.isSystemUserRequired(),
        row.getDescriptor(), appIdsById.getOrDefault(row.getId(), Set.of()));
      if (moduleId.equals(row.getId())) {
        self = resolved;
      } else {
        providers.add(resolved);
      }
    }
    return new ModuleBootstrapData(self, List.copyOf(providers));
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -Dcheckstyle.skip test -Dtest=ModuleBootstrapDataTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/folio/am/service/ModuleBootstrapData.java \
        src/test/java/org/folio/am/service/ModuleBootstrapDataTest.java
git commit -m "feat(bootstrap): add application-independent module bootstrap snapshot

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Cacheable data provider

**Files:**
- Create: `src/main/java/org/folio/am/service/ModuleBootstrapDataProvider.java`
- Test: `src/test/java/org/folio/am/service/ModuleBootstrapDataProviderCacheTest.java`

- [ ] **Step 1: Write the failing cache slice test**

This test boots a minimal Spring context (so the `@Cacheable` proxy is active) with the cache enabled and the repository mocked, and asserts the DB is hit only once for repeated calls, and again after eviction.

```java
package org.folio.am.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.am.support.TestValues.moduleBootstrapView;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.am.config.cache.BootstrapCacheConfiguration;
import org.folio.am.repository.ModuleBootstrapRepository;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@UnitTest
@SpringBootTest(classes = {BootstrapCacheConfiguration.class, ModuleBootstrapDataProvider.class})
@TestPropertySource(properties = "application.bootstrap-cache.enabled=true")
class ModuleBootstrapDataProviderCacheTest {

  @Autowired private ModuleBootstrapDataProvider provider;
  @Autowired private CacheManager cacheManager;
  @MockitoBean private ModuleBootstrapRepository repository;

  @Test
  void getData_cachesByModuleId_andRefetchesAfterEvict() {
    when(repository.findAllRequiredByModuleId("mod-foo-1.0.0"))
      .thenReturn(List.of(moduleBootstrapView("mod-foo-1.0.0", "foo-int")));
    when(repository.findApplicationIdsByModuleId("mod-foo-1.0.0")).thenReturn(List.of());

    provider.getData("mod-foo-1.0.0");
    provider.getData("mod-foo-1.0.0");
    verify(repository, times(1)).findAllRequiredByModuleId("mod-foo-1.0.0");

    cacheManager.getCache(BootstrapCacheConfiguration.MODULE_BOOTSTRAP_CACHE).clear();

    provider.getData("mod-foo-1.0.0");
    verify(repository, times(2)).findAllRequiredByModuleId("mod-foo-1.0.0");
    assertThat(cacheManager.getCacheNames()).contains(BootstrapCacheConfiguration.MODULE_BOOTSTRAP_CACHE);
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -Dcheckstyle.skip test -Dtest=ModuleBootstrapDataProviderCacheTest`
Expected: FAIL/compile error — `ModuleBootstrapDataProvider` does not exist.

- [ ] **Step 3: Create the provider**

`src/main/java/org/folio/am/service/ModuleBootstrapDataProvider.java`:

```java
package org.folio.am.service;

import lombok.RequiredArgsConstructor;
import org.folio.am.config.cache.BootstrapCacheConfiguration;
import org.folio.am.repository.ModuleBootstrapRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single DB-touching, cached entry point for module bootstrap data. Lives in its own bean (not on
 * {@link ModuleBootstrapService}) so the cache proxy is honored — a self-invocation from the service
 * would bypass it.
 */
@Component
@RequiredArgsConstructor
public class ModuleBootstrapDataProvider {

  private final ModuleBootstrapRepository repository;

  @Cacheable(cacheNames = BootstrapCacheConfiguration.MODULE_BOOTSTRAP_CACHE, key = "#moduleId")
  @Transactional(readOnly = true)
  public ModuleBootstrapData getData(String moduleId) {
    var rows = repository.findAllRequiredByModuleId(moduleId);
    var appIdRows = repository.findApplicationIdsByModuleId(moduleId);
    return ModuleBootstrapData.from(moduleId, rows, appIdRows);
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -Dcheckstyle.skip test -Dtest=ModuleBootstrapDataProviderCacheTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/folio/am/service/ModuleBootstrapDataProvider.java \
        src/test/java/org/folio/am/service/ModuleBootstrapDataProviderCacheTest.java
git commit -m "feat(bootstrap): add cacheable module bootstrap data provider

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Shared cache evictor

**Files:**
- Create: `src/main/java/org/folio/am/service/BootstrapCacheEvictor.java`
- Test: `src/test/java/org/folio/am/service/BootstrapCacheEvictorTest.java`

- [ ] **Step 1: Create the evictor**

`src/main/java/org/folio/am/service/BootstrapCacheEvictor.java`:

```java
package org.folio.am.service;

import lombok.extern.log4j.Log4j2;
import org.folio.am.config.cache.BootstrapCacheConfiguration;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

/**
 * Single point of full-flush invalidation for the module-bootstrap cache, shared by the in-process
 * and Kafka invalidators. Lives in its own bean so the {@code @CacheEvict} proxy is honored. Full
 * flush (not per-module) because a discovery change to module B can affect any module that depends
 * on an interface B provides (fan-out). No-op when the active cache manager is the {@code NoOp} one.
 */
@Log4j2
@Component
public class BootstrapCacheEvictor {

  @CacheEvict(cacheNames = BootstrapCacheConfiguration.MODULE_BOOTSTRAP_CACHE, allEntries = true)
  public void evictAll() {
    log.debug("Evicting all module-bootstrap cache entries");
  }
}
```

- [ ] **Step 2: Add an isolation slice test (proves the `@CacheEvict` proxy actually clears entries)**

`src/test/java/org/folio/am/service/BootstrapCacheEvictorTest.java`:

```java
package org.folio.am.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.am.config.cache.BootstrapCacheConfiguration;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;

@UnitTest
@SpringBootTest(classes = {BootstrapCacheConfiguration.class, BootstrapCacheEvictor.class})
@TestPropertySource(properties = "application.bootstrap-cache.enabled=true")
class BootstrapCacheEvictorTest {

  @Autowired private BootstrapCacheEvictor evictor;
  @Autowired private CacheManager cacheManager;

  @Test
  void evictAll_clearsAllEntries() {
    var cache = cacheManager.getCache(BootstrapCacheConfiguration.MODULE_BOOTSTRAP_CACHE);
    cache.put("k", "v");

    evictor.evictAll();

    assertThat(cache.get("k")).isNull();
  }
}
```

- [ ] **Step 3: Run it to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -Dcheckstyle.skip test -Dtest=BootstrapCacheEvictorTest`
Expected: PASS. (If it fails with the entry still present, the `@CacheEvict` proxy is not active — confirm `BootstrapCacheEvictor` is a Spring bean and `@EnableCaching` is on `BootstrapCacheConfiguration`.)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/folio/am/service/BootstrapCacheEvictor.java \
        src/test/java/org/folio/am/service/BootstrapCacheEvictorTest.java
git commit -m "feat(bootstrap): add shared full-flush cache evictor

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: In-process afterCommit invalidator

**Files:**
- Create: `src/main/java/org/folio/am/service/BootstrapCacheInProcessInvalidator.java`
- Test: `src/test/java/org/folio/am/service/BootstrapCacheInProcessInvalidatorTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.folio.am.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.folio.am.domain.dto.ModuleDiscovery;
import org.folio.am.domain.entity.ModuleType;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@UnitTest
@ExtendWith(MockitoExtension.class)
class BootstrapCacheInProcessInvalidatorTest {

  @Mock private BootstrapCacheEvictor evictor;
  @InjectMocks private BootstrapCacheInProcessInvalidator invalidator;

  @AfterEach
  void cleanup() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void onDiscoveryCreate_evictsAfterCommit_whenTransactionActive() {
    TransactionSynchronizationManager.initSynchronization();

    invalidator.onDiscoveryCreate(new ModuleDiscovery().id("mod-foo-1.0.0"), ModuleType.BACKEND, "tok");

    // not evicted yet (still "in transaction")
    verify(evictor, never()).evictAll();

    // simulate commit
    for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
      sync.afterCommit();
    }
    verify(evictor).evictAll();
  }

  @Test
  void onDiscoveryDelete_evictsImmediately_whenNoTransaction() {
    invalidator.onDiscoveryDelete("mod-foo-1.0.0", "mod-foo-1.0.0", ModuleType.BACKEND, "tok");
    verify(evictor).evictAll();
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -Dcheckstyle.skip test -Dtest=BootstrapCacheInProcessInvalidatorTest`
Expected: FAIL/compile error — `BootstrapCacheInProcessInvalidator` does not exist.

- [ ] **Step 3: Create the invalidator**

`src/main/java/org/folio/am/service/BootstrapCacheInProcessInvalidator.java`:

```java
package org.folio.am.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.domain.dto.ModuleDiscovery;
import org.folio.am.domain.entity.ModuleType;
import org.folio.am.utils.ConditionalOnFarModeDisabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Evicts the module-bootstrap cache on the writing replica immediately after the discovery-change
 * transaction commits, tightening that replica's staleness window to ~zero (other replicas converge
 * via the Kafka broadcast). {@code afterCommit} avoids an in-transaction race where a concurrent read
 * could repopulate the cache with about-to-be-stale data. Active only when the cache is enabled and
 * FAR mode is off.
 */
@Log4j2
@Component
@RequiredArgsConstructor
@ConditionalOnFarModeDisabled
@ConditionalOnProperty(name = "application.bootstrap-cache.enabled", havingValue = "true", matchIfMissing = true)
public class BootstrapCacheInProcessInvalidator implements ApplicationDiscoveryListener {

  private final BootstrapCacheEvictor evictor;

  @Override
  public void onDiscoveryCreate(ModuleDiscovery moduleDiscovery, ModuleType type, String token) {
    evictAfterCommit();
  }

  @Override
  public void onDiscoveryUpdate(ModuleDiscovery moduleDiscovery, ModuleType type, String token) {
    evictAfterCommit();
  }

  @Override
  public void onDiscoveryDelete(String serviceId, String instanceId, ModuleType type, String token) {
    evictAfterCommit();
  }

  private void evictAfterCommit() {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      evictor.evictAll();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        log.debug("Invalidating module-bootstrap cache after discovery change commit");
        evictor.evictAll();
      }
    });
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -Dcheckstyle.skip test -Dtest=BootstrapCacheInProcessInvalidatorTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/folio/am/service/BootstrapCacheInProcessInvalidator.java \
        src/test/java/org/folio/am/service/BootstrapCacheInProcessInvalidatorTest.java
git commit -m "feat(bootstrap): evict bootstrap cache after discovery change commit

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: Kafka broadcast invalidation consumer

**Files:**
- Create: `src/main/java/org/folio/am/integration/kafka/config/BootstrapCacheConsumerConfiguration.java`
- Create: `src/main/java/org/folio/am/integration/kafka/BootstrapCacheInvalidationListener.java`
- Test: `src/test/java/org/folio/am/integration/kafka/BootstrapCacheInvalidationListenerTest.java`

The end-to-end broadcast path is verified by the IT in Task 12; this task wires the beans, unit-tests the listener's evict call, and confirms compilation.

- [ ] **Step 1: Create the consumer configuration**

`src/main/java/org/folio/am/integration/kafka/config/BootstrapCacheConsumerConfiguration.java`:

```java
package org.folio.am.integration.kafka.config;

import static org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.folio.am.integration.kafka.model.DiscoveryEvent;
import org.folio.am.utils.ConditionalOnFarModeDisabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

@Log4j2
@Configuration
@RequiredArgsConstructor
@ConditionalOnFarModeDisabled
@ConditionalOnProperty(name = "application.bootstrap-cache.enabled", havingValue = "true", matchIfMissing = true)
public class BootstrapCacheConsumerConfiguration {

  private final KafkaProperties kafkaProperties;

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, DiscoveryEvent>
    bootstrapCacheKafkaListenerContainerFactory(ConsumerFactory<String, DiscoveryEvent> consumerFactory) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, DiscoveryEvent>();
    factory.setConsumerFactory(consumerFactory);
    factory.setCommonErrorHandler(errorHandler());
    return factory;
  }

  @Bean
  public ConsumerFactory<String, DiscoveryEvent> bootstrapCacheConsumerFactory() {
    var deserializer = new JacksonJsonDeserializer<>(DiscoveryEvent.class);
    Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties());
    config.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
    // Broadcast: a unique group per instance so EVERY replica receives EVERY event.
    config.put(GROUP_ID_CONFIG, "mgr-applications-bootstrap-cache-" + UUID.randomUUID());
    // A fresh replica starts cold, so consume only events from start-up onward.
    config.put(AUTO_OFFSET_RESET_CONFIG, "latest");
    return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
  }

  private DefaultErrorHandler errorHandler() {
    var handler = new DefaultErrorHandler((record, ex) ->
      log.warn("Failed to process discovery event [record: {}]", record, ex.getCause()));
    // best-effort: evict-all is idempotent, so no retry/backoff
    handler.setBackOffFunction((record, ex) -> new FixedBackOff(0L, 0L));
    handler.setLogLevel(KafkaException.Level.INFO);
    return handler;
  }
}
```

The single-arg `DefaultErrorHandler(ConsumerRecordRecoverer)` constructor plus `setBackOffFunction` mirrors the proven pattern in `EntitlementConsumerConfiguration.java:56-63`.

- [ ] **Step 2: Create the listener**

`src/main/java/org/folio/am/integration/kafka/BootstrapCacheInvalidationListener.java`:

```java
package org.folio.am.integration.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.am.integration.kafka.model.DiscoveryEvent;
import org.folio.am.service.BootstrapCacheEvictor;
import org.folio.am.utils.ConditionalOnFarModeDisabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes discovery events from {@code {ENV}.discovery} (per-instance group, broadcast) and
 * full-flushes the module-bootstrap cache on this replica. Invalidation is idempotent, so outbox
 * replay / duplicate delivery is harmless.
 */
@Log4j2
@Component
@RequiredArgsConstructor
@ConditionalOnFarModeDisabled
@ConditionalOnProperty(name = "application.bootstrap-cache.enabled", havingValue = "true", matchIfMissing = true)
public class BootstrapCacheInvalidationListener {

  private final BootstrapCacheEvictor evictor;

  @KafkaListener(
    id = "bootstrap-cache-invalidation-listener",
    containerFactory = "bootstrapCacheKafkaListenerContainerFactory",
    topics = "${spring.kafka.topics.discovery}")
  public void onDiscoveryEvent(DiscoveryEvent event) {
    log.debug("Invalidating module-bootstrap cache on discovery event: moduleId = {}",
      event == null ? null : event.getModuleId());
    evictor.evictAll();
  }
}
```

- [ ] **Step 3: Unit-test the listener's evict call**

`src/test/java/org/folio/am/integration/kafka/BootstrapCacheInvalidationListenerTest.java`:

```java
package org.folio.am.integration.kafka;

import static org.mockito.Mockito.verify;

import org.folio.am.integration.kafka.model.DiscoveryEvent;
import org.folio.am.service.BootstrapCacheEvictor;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class BootstrapCacheInvalidationListenerTest {

  @Mock private BootstrapCacheEvictor evictor;
  @InjectMocks private BootstrapCacheInvalidationListener listener;

  @Test
  void onDiscoveryEvent_evictsAll() {
    listener.onDiscoveryEvent(new DiscoveryEvent("mod-x-1.0.0")); // @AllArgsConstructor: single String arg
    verify(evictor).evictAll();
  }
}
```

`DiscoveryEvent` is `@Data @AllArgsConstructor` with a single `moduleId` field — construct it with `new DiscoveryEvent("...")` (no no-arg/fluent form).

- [ ] **Step 4: Run it and compile**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -Dcheckstyle.skip test -Dtest=BootstrapCacheInvalidationListenerTest`
Expected: PASS. Then `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -DskipTests compile` → BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/folio/am/integration/kafka/config/BootstrapCacheConsumerConfiguration.java \
        src/main/java/org/folio/am/integration/kafka/BootstrapCacheInvalidationListener.java \
        src/test/java/org/folio/am/integration/kafka/BootstrapCacheInvalidationListenerTest.java
git commit -m "feat(bootstrap): broadcast discovery-driven bootstrap cache invalidation

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 10: Refactor ModuleBootstrapService onto the snapshot

This is the behavior-preserving core. The existing ITs (`ModuleBootstrapIT`, `ModuleScopedBootstrapIT`, `ModuleBootstrapControllerTest`) are the end-to-end characterization and must stay green (they run with the cache disabled, per Task 3).

**Files:**
- Modify: `src/main/java/org/folio/am/service/ModuleBootstrapService.java`
- Modify: `src/test/java/org/folio/am/service/ModuleBootstrapServiceTest.java`
- Modify: `src/test/java/org/folio/am/it/ModuleScopedBootstrapIT.java`
- Create: `src/test/resources/sql/module-bootstrap-shared-provider.sql`

Behavior-preservation note: the existing bootstrap ITs use **single-application-per-module** fixtures (`ModuleBootstrapIT` puts `mod-provider` in `provider-app` and `provider-and-consumer` in `test-app`; the STRICT-JSON assertions on `applicationId` are single-valued). So `representativeApp` (smallest matching app) returns exactly the value the old arbitrary `SELECT DISTINCT` returned — the existing assertions are unaffected. The deterministic min only changes behavior for genuinely shared modules, which no existing assertion pins.

- [ ] **Step 1: Rewrite the unit test to mock the data provider**

Replace the body of `src/test/java/org/folio/am/service/ModuleBootstrapServiceTest.java` with the following. It mocks `ModuleBootstrapDataProvider`, drives the in-memory derivations, and adds the shared-module egress case.

```java
package org.folio.am.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.am.service.ModuleBootstrapData.ResolvedModule;
import static org.folio.am.support.TestConstants.APPLICATION_ID;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Set;
import org.folio.am.mapper.ModuleBootstrapMapperImpl;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.RoutingEntry;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ModuleBootstrapServiceTest {

  private static final String FOO = "test-module-foo-1.0.0";
  private static final String BAR = "test-module-bar-1.0.0";
  private static final String BAR_INT = "test-bar-interface";

  @Mock private ModuleBootstrapDataProvider dataProvider;
  private ModuleBootstrapService service;

  @BeforeEach
  void setUp() {
    service = new ModuleBootstrapService(dataProvider, new ModuleBootstrapMapperImpl());
  }

  private static ResolvedModule resolved(String id, Set<String> apps, ModuleDescriptor descriptor) {
    return new ResolvedModule(id, "http://" + id + ":8080", false, descriptor, apps);
  }

  private static ModuleDescriptor consumerDescriptor() {
    return new ModuleDescriptor().requires(List.of(new InterfaceReference().id(BAR_INT)));
  }

  private static ModuleDescriptor providerDescriptor() {
    return new ModuleDescriptor().provides(List.of(new InterfaceDescriptor().id(BAR_INT)
      .interfaceType("multiple").addHandlersItem(new RoutingEntry().addMethodsItem("GET").path("/x"))));
  }

  @Test
  void getById_returnsModuleAndRequiredProviders() {
    var self = resolved(FOO, Set.of(APPLICATION_ID), consumerDescriptor());
    var provider = resolved(BAR, Set.of(APPLICATION_ID), providerDescriptor());
    when(dataProvider.getData(FOO)).thenReturn(new ModuleBootstrapData(self, List.of(provider)));

    var actual = service.getById(FOO);

    assertThat(actual.getModule().getModuleId()).isEqualTo(FOO);
    assertThat(actual.getRequiredModules()).singleElement()
      .satisfies(m -> assertThat(m.getModuleId()).isEqualTo(BAR));
  }

  @Test
  void getById_throws_whenModuleAbsent() {
    when(dataProvider.getData(FOO)).thenReturn(new ModuleBootstrapData(null, List.of()));
    assertThatThrownBy(() -> service.getById(FOO))
      .isInstanceOf(EntityNotFoundException.class)
      .hasMessage("Module not found by id: " + FOO);
  }

  @Test
  void getIngressBootstrap_returnsModuleOnly() {
    var self = resolved(FOO, Set.of(APPLICATION_ID), providerDescriptor());
    when(dataProvider.getData(FOO)).thenReturn(new ModuleBootstrapData(self, List.of()));

    var actual = service.getIngressBootstrap(FOO);

    assertThat(actual.getModule().getModuleId()).isEqualTo(FOO);
    assertThat(actual.getRequiredModules()).isEmpty();
  }

  @Test
  void getEgressBootstrap_emptyApplicationIds_returnsNotFound() {
    var actual = service.getEgressBootstrap(FOO, List.of());
    assertThat(actual.getFound()).isFalse();
  }

  @Test
  void getEgressBootstrap_sharedProviderInScopeViaSecondApp_isIncluded() {
    var self = resolved(FOO, Set.of("app-a-1.0.0"), consumerDescriptor());
    // shared provider belongs to BOTH apps; scope contains only the second
    var provider = resolved(BAR, Set.of("app-a-1.0.0", "app-b-1.0.0"), providerDescriptor());
    when(dataProvider.getData(FOO)).thenReturn(new ModuleBootstrapData(self, List.of(provider)));

    var actual = service.getEgressBootstrap(FOO, List.of("app-a-1.0.0"));

    assertThat(actual.getFound()).isTrue();
    assertThat(actual.getBootstrap().getRequiredModules()).singleElement()
      .satisfies(m -> assertThat(m.getModuleId()).isEqualTo(BAR));
  }

  @Test
  void getEgressBootstrap_selfOutsideScope_returnsNotFound() {
    var self = resolved(FOO, Set.of("app-a-1.0.0"), consumerDescriptor());
    when(dataProvider.getData(FOO)).thenReturn(new ModuleBootstrapData(self, List.of()));

    var actual = service.getEgressBootstrap(FOO, List.of("app-b-1.0.0"));
    assertThat(actual.getFound()).isFalse();
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -Dcheckstyle.skip test -Dtest=ModuleBootstrapServiceTest`
Expected: FAIL/compile error — `ModuleBootstrapService` still has the old constructor `(repository, mapper)` and old logic.

- [ ] **Step 3: Rewrite the service**

Replace `src/main/java/org/folio/am/service/ModuleBootstrapService.java` with:

```java
package org.folio.am.service;

import static org.folio.am.utils.ModuleIdUtils.getNameAndVersion;
import static org.folio.common.utils.CollectionUtils.toStream;

import jakarta.persistence.EntityNotFoundException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.folio.am.domain.dto.EgressBootstrapResult;
import org.folio.am.domain.dto.ModuleBootstrap;
import org.folio.am.domain.dto.ModuleBootstrapDiscovery;
import org.folio.am.mapper.ModuleBootstrapMapper;
import org.folio.am.service.ModuleBootstrapData.ResolvedModule;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.utils.InterfaceComparisonUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ModuleBootstrapService {

  private final ModuleBootstrapDataProvider dataProvider;
  private final ModuleBootstrapMapper mapper;

  /**
   * Full module bootstrap: the module plus the highest-version providers of every interface it
   * requires/optional, across all applications.
   */
  public ModuleBootstrap getById(String moduleId) {
    var data = dataProvider.getData(moduleId);
    var self = requireSelf(data, moduleId);
    return new ModuleBootstrap()
      .module(toDiscovery(self, self.descriptor().getProvides(), null))
      .requiredModules(requiredModules(self, data.providers(), null));
  }

  /**
   * Ingress bootstrap: this module's own routes only.
   */
  public ModuleBootstrap getIngressBootstrap(String moduleId) {
    var data = dataProvider.getData(moduleId);
    var self = requireSelf(data, moduleId);
    return new ModuleBootstrap()
      .module(toDiscovery(self, self.descriptor().getProvides(), null))
      .requiredModules(List.of());
  }

  /**
   * Egress bootstrap scoped to the given applications.
   */
  public EgressBootstrapResult getEgressBootstrap(String moduleId, List<String> applicationIds) {
    if (applicationIds == null || applicationIds.isEmpty()) {
      return new EgressBootstrapResult().found(false);
    }
    var scope = Set.copyOf(applicationIds);
    var data = dataProvider.getData(moduleId);
    var self = data.self();
    if (self == null || disjoint(self.applicationIds(), scope)) {
      return new EgressBootstrapResult().found(false);
    }
    var providers = data.providers().stream()
      .filter(p -> !disjoint(p.applicationIds(), scope))
      .toList();
    return new EgressBootstrapResult().found(true).bootstrap(new ModuleBootstrap()
      .module(toDiscovery(self, self.descriptor().getProvides(), scope))
      .requiredModules(requiredModules(self, providers, scope)));
  }

  private List<ModuleBootstrapDiscovery> requiredModules(ResolvedModule self, List<ResolvedModule> providers,
    Set<String> scope) {
    var requiredInterfaceIds = requiredInterfaceIds(self);
    if (requiredInterfaceIds.isEmpty()) {
      return List.of();
    }
    var discoveries = providers.stream()
      .map(p -> toDiscovery(p, narrow(p, requiredInterfaceIds), scope))
      .collect(Collectors.toList());
    return deduplicate(discoveries);
  }

  private ModuleBootstrapDiscovery toDiscovery(ResolvedModule module, List<InterfaceDescriptor> provides,
    Set<String> scope) {
    return new ModuleBootstrapDiscovery()
      .moduleId(module.id())
      .applicationId(representativeApp(module, scope))
      .location(module.location())
      .systemUserRequired(module.systemUserRequired())
      .interfaces(toStream(provides).map(mapper::convert).collect(Collectors.toList()));
  }

  private static List<InterfaceDescriptor> narrow(ResolvedModule provider, Set<String> requiredInterfaceIds) {
    return toStream(provider.descriptor().getProvides())
      .filter(i -> requiredInterfaceIds.contains(i.getId()))
      .collect(Collectors.toList());
  }

  private static Set<String> requiredInterfaceIds(ResolvedModule self) {
    var descriptor = self.descriptor();
    return Stream.concat(toStream(descriptor.getRequires()), toStream(descriptor.getOptional()))
      .map(InterfaceReference::getId)
      .collect(Collectors.toSet());
  }

  private static String representativeApp(ResolvedModule module, Set<String> scope) {
    return module.applicationIds().stream()
      .filter(app -> scope == null || scope.contains(app))
      .min(Comparator.naturalOrder())
      .orElse(null);
  }

  private static boolean disjoint(Set<String> a, Set<String> b) {
    return Collections.disjoint(a, b);
  }

  private static ResolvedModule requireSelf(ModuleBootstrapData data, String moduleId) {
    var self = data.self();
    if (self == null) {
      throw new EntityNotFoundException("Module not found by id: " + moduleId);
    }
    return self;
  }

  private static List<ModuleBootstrapDiscovery> deduplicate(List<ModuleBootstrapDiscovery> requiredModules) {
    var results = new HashMap<String, ModuleBootstrapDiscovery>();
    for (var discovery : requiredModules) {
      var nameAndVersion = getNameAndVersion(discovery.getModuleId());
      var name = nameAndVersion.getLeft();
      var version = nameAndVersion.getRight();
      var existing = results.get(name);
      if (existing == null) {
        results.put(name, discovery);
      } else {
        var existingVersion = getNameAndVersion(existing.getModuleId()).getRight();
        if (InterfaceComparisonUtils.compare("", version, "", existingVersion) > 0) {
          results.put(name, discovery);
        }
      }
    }
    return results.values().stream().toList();
  }
}
```

Note: the self module's `module` field passes `self.descriptor().getProvides()` unfiltered (full provides), matching the current `mapper.convert(moduleView)`; provider provides are narrowed to the required interfaces. `toStream(...)` (folio `CollectionUtils`) null-safes a null `provides`. The destructive `removeIf`/`list.remove` are gone — derivations only read the cached descriptors.

- [ ] **Step 4: Run the unit test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -Dcheckstyle.skip test -Dtest=ModuleBootstrapServiceTest`
Expected: PASS (6 tests).

- [ ] **Step 5: Add a deterministic end-to-end shared-module egress IT**

Create the fixture `src/test/resources/sql/module-bootstrap-shared-provider.sql`. The shared provider `mod-provider` lives in **two** applications; the descriptor JSON carries `requires`/`provides` (the service reads required interfaces from the descriptor) **and** `module_interface_reference` carries the same edges (the repository query walks that table) — both must agree:

```sql
INSERT INTO application(id, name, version, application_descriptor) VALUES
  ('app-consumer-1.0.0', 'app-consumer', '1.0.0', '{"id": "app-consumer-1.0.0"}'),
  ('app-prov-a-1.0.0', 'app-prov-a', '1.0.0', '{"id": "app-prov-a-1.0.0"}'),
  ('app-prov-b-1.0.0', 'app-prov-b', '1.0.0', '{"id": "app-prov-b-1.0.0"}');

INSERT INTO module(id, name, version, discovery_url, descriptor, type) VALUES
  ('mod-consumer-1.0.0', 'mod-consumer', '1.0.0', 'http://mod-consumer:8080',
    '{"id": "mod-consumer-1.0.0", "requires": [{"id": "shared-int", "version": "1.0"}]}', 'BACKEND'),
  ('mod-provider-1.0.0', 'mod-provider', '1.0.0', 'http://mod-provider:8080',
    '{"id": "mod-provider-1.0.0", "provides": [{"id": "shared-int", "version": "1.0", "interfaceType": "multiple"}]}', 'BACKEND');

INSERT INTO application_module(application_id, module_id) VALUES
  ('app-consumer-1.0.0', 'mod-consumer-1.0.0'),
  ('app-prov-a-1.0.0', 'mod-provider-1.0.0'),
  ('app-prov-b-1.0.0', 'mod-provider-1.0.0');

INSERT INTO module_interface_reference(module_id, id, version, type) VALUES
  ('mod-consumer-1.0.0', 'shared-int', '1.0', 'REQUIRES'),
  ('mod-provider-1.0.0', 'shared-int', '1.0', 'PROVIDES');
```

Add to `src/test/java/org/folio/am/it/ModuleScopedBootstrapIT.java` (new test method; the `it` profile keeps the cache off, so this exercises the projection + derivation through the real DB). The scope contains the consumer app + the provider's **second** app only — the case the naive cached-`DISTINCT` approach gets wrong:

```java
  @Test
  @Sql(scripts = "classpath:/sql/module-bootstrap-shared-provider.sql")
  void postModuleBootstrap_egress_sharedProviderReachableViaSecondApp_found() throws Exception {
    var request = new ModuleBootstrapRequest()
      .type(ModuleBootstrapRequest.TypeEnum.EGRESS)
      .applicationIds(List.of("app-consumer-1.0.0", "app-prov-b-1.0.0"));

    mockMvc.perform(post("/modules/{id}/bootstrap", "mod-consumer-1.0.0")
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON)
        .content(asJsonString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.egress.found").value(true))
      .andExpect(jsonPath("$.egress.bootstrap.requiredModules[0].moduleId").value("mod-provider-1.0.0"));
  }
```

Add the imports this method needs to the class if not already present: `org.springframework.test.context.jdbc.Sql`, `org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath` (static). The class already truncates `AFTER_TEST_METHOD`, so the fixture is cleaned up.

- [ ] **Step 6: Run the affected ITs to verify behavior is preserved**

Run each and expect PASS:
```
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -Dcheckstyle.skip failsafe:integration-test -Dit.test=**/ModuleScopedBootstrapIT.java
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -Dcheckstyle.skip failsafe:integration-test -Dit.test=**/ModuleBootstrapIT.java
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -Dcheckstyle.skip test -Dtest=ModuleBootstrapControllerTest
```

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/folio/am/service/ModuleBootstrapService.java \
        src/test/java/org/folio/am/service/ModuleBootstrapServiceTest.java \
        src/test/java/org/folio/am/it/ModuleScopedBootstrapIT.java \
        src/test/resources/sql/module-bootstrap-shared-provider.sql
git commit -m "refactor(bootstrap): derive bootstrap from cached app-independent snapshot

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 11: Remove the now-unused scoped query

`getEgressBootstrap` no longer calls `findAllRequiredByModuleIdInApplications` (the only caller). Remove it and its repository tests.

**Files:**
- Modify: `src/main/java/org/folio/am/repository/ModuleBootstrapRepository.java`
- Modify: `src/test/java/org/folio/am/repository/ModuleBootstrapRepositoryIT.java`

- [ ] **Step 1: Confirm there are no other callers**

Run: `grep -rn "findAllRequiredByModuleIdInApplications" src/`
Expected: matches only in `ModuleBootstrapRepository.java` (declaration) and `ModuleBootstrapRepositoryIT.java` (tests). If anything else references it, stop and reassess.

- [ ] **Step 2: Delete the method**

In `src/main/java/org/folio/am/repository/ModuleBootstrapRepository.java`, delete the `findAllRequiredByModuleIdInApplications` method and its Javadoc.

- [ ] **Step 3: Delete its tests**

In `src/test/java/org/folio/am/repository/ModuleBootstrapRepositoryIT.java`, delete the three test methods that call it: `findAllRequiredByModuleIdInApplications_returnsOnlyInScopeModules`, `findAllRequiredByModuleIdInApplications_returnsAllWhenAllAppsInScope`, `findAllRequiredByModuleIdInApplications_excludesRequestedModuleWhenOutOfScope`.

- [ ] **Step 4: Run the repository IT to verify it still passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -Dcheckstyle.skip failsafe:integration-test -Dit.test=**/ModuleBootstrapRepositoryIT.java`
Expected: PASS (remaining tests, including the app-id projection test).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/folio/am/repository/ModuleBootstrapRepository.java \
        src/test/java/org/folio/am/repository/ModuleBootstrapRepositoryIT.java
git commit -m "refactor(bootstrap): drop unused scoped egress query

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 12: End-to-end invalidation IT

Proves that, with the cache **enabled**, a discovery change refreshes the bootstrap result — exercising the in-process `afterCommit` evict (discovery created via the API) and the wired-up Kafka consumer.

**Files:**
- Create: `src/test/java/org/folio/am/it/BootstrapCacheInvalidationIT.java`

- [ ] **Step 1: Write the IT**

```java
package org.folio.am.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.folio.am.support.TestUtils.generateAccessToken;
import static org.folio.am.support.TestValues.moduleDiscovery;
import static org.folio.test.TestUtils.asJsonString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.folio.am.domain.dto.ApplicationDescriptor;
import org.folio.am.domain.dto.Module;
import org.folio.am.support.base.BaseIntegrationTest;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.security.integration.keycloak.configuration.properties.KeycloakProperties;
import org.folio.test.extensions.EnableKeycloakDataImport;
import org.folio.test.extensions.EnableKeycloakSecurity;
import org.folio.test.extensions.EnableKeycloakTlsMode;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@EnableKeycloakSecurity
@EnableKeycloakTlsMode
@EnableKeycloakDataImport
@TestPropertySource(properties = {
  "application.okapi.enabled=false",
  "application.kong.enabled=false",
  "application.bootstrap-cache.enabled=true"
})
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
class BootstrapCacheInvalidationIT extends BaseIntegrationTest {

  @Autowired KeycloakProperties keycloakProperties;

  @Test
  void discoveryChange_invalidatesBootstrapCache() throws Exception {
    postApplication(new ApplicationDescriptor()
      .name("cache-app").version("1.0.0")
      .modules(List.of(new Module().name("mod-cache").version("1.0.0")))
      .moduleDescriptors(List.of(new ModuleDescriptor()
        .id("mod-cache-1.0.0")
        .provides(List.of(new InterfaceDescriptor().id("cache-int").version("1.0").interfaceType("multiple"))))));

    createDiscovery("mod-cache-1.0.0", "http://mod-cache-1:8081");

    // warm the cache
    getBootstrap("mod-cache-1.0.0").andExpect(jsonPath("$.module.location").value("http://mod-cache-1:8081"));

    // change discovery -> in-process afterCommit evict
    updateDiscovery("mod-cache-1.0.0", "http://mod-cache-2:8081");

    await().atMost(TEN_SECONDS).untilAsserted(() ->
      getBootstrap("mod-cache-1.0.0").andExpect(jsonPath("$.module.location").value("http://mod-cache-2:8081")));
  }

  private org.springframework.test.web.servlet.ResultActions getBootstrap(String moduleId) throws Exception {
    return mockMvc.perform(get("/modules/{id}/bootstrap", moduleId)
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk());
  }

  private void createDiscovery(String moduleId, String location) throws Exception {
    var name = moduleId.substring(0, moduleId.lastIndexOf('-'));
    var version = moduleId.substring(moduleId.lastIndexOf('-') + 1);
    mockMvc.perform(post("/modules/{id}/discovery", moduleId)
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON)
        .content(asJsonString(moduleDiscovery(name, version, location))))
      .andExpect(status().isCreated());
  }

  private void updateDiscovery(String moduleId, String location) throws Exception {
    var name = moduleId.substring(0, moduleId.lastIndexOf('-'));
    var version = moduleId.substring(moduleId.lastIndexOf('-') + 1);
    mockMvc.perform(put("/modules/{id}/discovery", moduleId)
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON)
        .content(asJsonString(moduleDiscovery(name, version, location))))
      .andExpect(status().isNoContent());
  }

  private void postApplication(ApplicationDescriptor descriptor) throws Exception {
    mockMvc.perform(post("/applications").content(asJsonString(descriptor))
        .header(TOKEN, generateAccessToken(keycloakProperties))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isCreated());
  }
}
```

Endpoint contract (verified against `ModuleDiscoveryController` + `am.yaml`): `PUT /modules/{id}/discovery` returns `204 No Content` and `ModuleDiscoveryService.update` fires `publishDiscoveryUpdate` — so the in-process `afterCommit` evict triggers. The assertion logic (location flips from `…-1` to `…-2` after the change) is the point.

- [ ] **Step 2: Run the IT**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -Dcheckstyle.skip failsafe:integration-test -Dit.test=**/BootstrapCacheInvalidationIT.java`
Expected: PASS. If the first `await` assertion flakes, the cache is not being invalidated — check that `BootstrapCacheInProcessInvalidator` is registered (FAR off, cache enabled) and that the discovery update fires `publishDiscoveryUpdate`.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/folio/am/it/BootstrapCacheInvalidationIT.java
git commit -m "test(bootstrap): verify discovery change invalidates bootstrap cache

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 13: Full verification

**Files:** none (verification + final commit if anything was tidied).

- [ ] **Step 1: Full build with unit tests + checkstyle**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q clean install -DskipITs` (or `-DskipTests=false` with surefire only)
Expected: BUILD SUCCESS, checkstyle clean. Fix any checkstyle violations (method length ≤ 21 lines; unused imports) inline and re-run.

- [ ] **Step 2: Run the full integration suite**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q verify`
Expected: BUILD SUCCESS — all ITs green, including the existing bootstrap ITs (cache off) and the new cache/invalidation ITs.

- [ ] **Step 3: Sanity-check the diff**

Run: `git log --oneline master..HEAD` and `git diff --stat master..HEAD`
Expected: the commits from Tasks 1–12; no stray files; the spec + plan docs present.

- [ ] **Step 4: Final commit (only if Step 1 required fixups)**

```bash
git add -A
git commit -m "chore(bootstrap): checkstyle and cleanup for bootstrap caching

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Notes for the implementer

- **Behavior preservation is the contract.** Tasks 5–11 change *how* bootstrap is computed, not *what* it returns. The existing `ModuleBootstrapIT`, `ModuleScopedBootstrapIT`, `ModuleBootstrapControllerTest`, and `ModuleBootstrapMapperTest` are the guardrails — they run with the cache disabled and must stay green throughout.
- **The cache is off in the `it` profile by design** (Task 3), so the broad IT suite is unaffected and free of cross-method cache-staleness surprises. Caching is exercised only by `ModuleBootstrapDataProviderCacheTest` (Task 6) and `BootstrapCacheInvalidationIT` (Task 12).
- **Spring Boot 4 package note:** `KafkaProperties` is `org.springframework.boot.kafka.autoconfigure.KafkaProperties` (as used by the existing `EntitlementConsumerConfiguration`), not the Boot 3 `org.springframework.boot.autoconfigure.kafka` package.
- **Why full-flush, not per-module evict:** a discovery change to module B must invalidate every module whose bootstrap depends on an interface B provides (fan-out); `DiscoveryEvent` carries only B's id, so per-module evict would miss the dependents.
- **Consistency bound:** cross-replica invalidation propagates via outbox → Kafka (sub-second), not instantly; the writing replica is tightened to ~zero by the in-process `afterCommit` evict. This is documented in the spec (§5) and is inherent to per-replica local caches without a distributed lock.
