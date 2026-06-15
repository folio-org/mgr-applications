# mgr-applications

Spring Boot 4.0.3 Application Manager for FOLIO: manages application/module lifecycle (registration, discovery, deployment, validation). Java 21, PostgreSQL/JPA, Liquibase, Kafka, Kong, Keycloak, OpenAPI codegen, MapStruct, Lombok.

## Build & Test

```bash
mvn clean install              # full build
mvn clean install -DskipTests  # skip tests
mvn test                       # unit tests (@Tag("unit"), *Test.java)
mvn verify                     # integration tests (@Tag("integration"), *IT.java; Testcontainers Postgres+Kafka)
mvn test -Dtest=ApplicationServiceTest#shouldCreateApplication  # single test
mvn checkstyle:check           # runs during build, FOLIO rules
```

Run locally: `java -Dokapi.url=... -Dokapi.token=... -jar target/mgr-applications-*.jar`. Full env vars in `README.md`.

## Architecture

Layers: Controllers (implement OpenAPI-generated interfaces) → Services → Repositories/Entities → Integration.

**Domain** (`org.folio.am`): `ApplicationEntity` (JSONB `ApplicationDescriptor`) ⇄ `ModuleEntity` (JSONB `ModuleDescriptor`, BACKEND/UI) is many-to-many via `application_module`. `ModuleDiscoveryEntity` = modules with non-null `discovery_url` (`@SQLRestriction`). `InterfaceReferenceEntity` tracks provided/required interfaces.

**Key services**: `ApplicationService` (CRUD, versioning, validation orchestration), `DependenciesValidator`, `ModuleDiscoveryService`, `ModuleBootstrapService`.

**Repositories** extend `JpaCqlRepository` for CQL filtering (e.g. `name=="app*"`) via `cql2pgjson`; use `findByQuery()`.

**Integrations**:
- Kong (`integration.kong`): `KongDiscoveryListener` syncs discovery → Kong services/routes. Toggle `KONG_INTEGRATION_ENABLED`.
- Kafka (`integration.kafka`): `DiscoveryPublisher` → `${ENV}.discovery`; `EntitlementEventListener` consumes `${ENV}.entitlement` (ENTITLE/UPGRADE/REVOKE), updates Kong tenant routes when `KONG_TENANT_CHECKS_ENABLED=true`.
- Keycloak (`integration.okapi`): resource-server/client/role/policy import; JWT validation. Toggle `KC_INTEGRATION_ENABLED`.
- mgr-tenant-entitlements (`integration.mte`): blocks deletion of entitled applications.

**Events** via `ApplicationEventPublisher`: `ApplicationDescriptorListener` (create/delete), `ApplicationDiscoveryListener` (discovery create/update/delete) drive Keycloak/Kong/Kafka side effects. Reliable publishing via transactional outbox (`integration.messaging`).

**FAR mode** (`FAR_MODE=true`): descriptor CRUD only; disables Kong/Kafka/Okapi/mte.

**Validation** (`VALIDATION_MODE`): NONE / BASIC / ON_CREATE (full dependency checks).

## Conventions

- **Codegen**: OpenAPI spec `src/main/resources/swagger.api/am.yaml` → `org.folio.am.domain.dto` + `org.folio.am.rest.resource` in `target/generated-sources` (do not edit). Add endpoint: edit spec → `mvn clean install` → implement generated interface.
- **DB**: Liquibase under `src/main/resources/changelog/` (`changelog-master.xml`); add new versioned changesets, never edit applied ones. JSONB for descriptors.
- **Transactions**: read-only by default on services; mark writes `@Transactional`.
- **Annotation order**: Lombok → Spring Config Processor → MapStruct (must match in IDE).
- **Conditional beans**: `@ConditionalOnProperty` per integration.
- **Logging**: Log4j2 (Spring default logging excluded). Secure stores: AWS-SSM/Vault/FSSP.
- **Unit tests**: never use lenient Mockito; stub only what's needed; verify only unmocked interactions; name `methodName_scenario_expectedBehavior`. Full guide: https://github.com/folio-org/folio-eureka-ai-dev/blob/master/docs/testing/unit-testing.md

## Key Dependencies

`folio-spring-cql`, `folio-security`, `folio-integration-kafka`, `folio-backend-common`, `folio-integration-kong`, `cql2pgjson`, `semver4j`.
