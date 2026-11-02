# mgr-applications

Application Manager for FOLIO: manages application/module lifecycle (registration, discovery, deployment, validation). Java, Spring Boot, PostgreSQL/JPA, Liquibase, Kafka, API Gateway (Kong or APISIX), Keycloak, OpenAPI codegen, MapStruct, Lombok.

## Build & Test

```bash
mvn clean install              # full build
mvn clean install -DskipTests  # skip tests
mvn test                       # unit tests (@Tag("unit"), *Test.java)
mvn verify                     # integration tests (@Tag("integration"), *IT.java; Testcontainers Postgres+Kafka+Kong; APISIX ITs use etcd+APISIX)
mvn test -Dtest=ApplicationServiceTest#create_positive  # single test
mvn checkstyle:check           # runs during build, FOLIO rules
```

Run locally: `java -jar target/mgr-applications-*.jar`. Full env vars in `README.md`.

## Architecture

**Integrations**:
- API Gateway (via `folio-integration-kong` or `folio-integration-apisix`, selected by `APIGW_TYPE`: `kong` default, `apisix` requires `APIGW_API_KEY`): self-registers the module's service + routes at startup (`ApiGatewayModuleRegistrar`, `MODULE_URL`, `APIGW_REGISTER_MODULE`). Toggle `APIGW_ENABLED`. Legacy `KONG_INTEGRATION_ENABLED` / `REGISTER_MODULE_IN_KONG` still work but are deprecated.
- Kafka (`integration.kafka`): `DiscoveryPublisher` publishes `${ENV}.discovery` events. The module descriptor
  declares this under `producesEvents` as the event identifier `mgr-applications.module.discovery.v1`
  (`DiscoveryPublisher.PRODUCED_EVENT_DISCOVERY`, deliberately decoupled from the topic name per the EUREKA-847
  naming-convention feedback) and declares no `consumesEvents` (guarded by `ModuleDescriptorTest`, MGRAPPS-130).
- Keycloak (via `folio-security`): resource-server/client/role/policy import; JWT validation. Toggle `KC_INTEGRATION_ENABLED`.
- mgr-tenant-entitlements (`integration.mte`): blocks deletion of entitled applications.

**FAR mode** (`FAR_MODE=true`): descriptor CRUD only; disables Kafka discovery publishing and mte. Keycloak integration (`KC_INTEGRATION_ENABLED`, default `true`) and gateway self-registration are not disabled by FAR mode — set `APIGW_ENABLED=false` explicitly.

**Validation** (`VALIDATION_MODE`): NONE / BASIC / ON_CREATE (only checks that each module has a URL or an inline descriptor). Interface dependency checks (`DependenciesValidator`) run only in `validate-interfaces` and `validate-descriptors`.

## Conventions

- **Codegen**: OpenAPI spec `src/main/resources/swagger.api/am.yaml` → `org.folio.am.domain.dto` + `org.folio.am.rest.resource` in `target/generated-sources` (do not edit). Add endpoint: edit spec → `mvn clean install` → implement generated interface.
- **DB**: Liquibase under `src/main/resources/changelog/` (`changelog-master.xml`); add new versioned changesets, never edit applied ones. JSONB for descriptors.
- **Transactions**: read-only by default on services; mark writes `@Transactional`.
- **Annotation order**: Lombok → Spring Config Processor → MapStruct (must match in IDE).
- **Conditional beans**: `@ConditionalOnProperty` per integration.
- **Logging**: Log4j2 (Spring default logging excluded). Secure stores: AWS-SSM/Vault/FSSP.
- **Unit tests**: never use lenient Mockito; stub only what's needed; verify only unmocked interactions; name `methodName_scenario_expectedBehavior`. Full guide: https://github.com/folio-org/folio-eureka-ai-dev/blob/master/docs/testing/unit-testing.md
- **Gateway ITs**: `BaseIntegrationTest` starts Kong via `@EnableKongGateway` (`KongRegistrationIT`). APISIX ITs add `@EnableApisixGateway` (`ApisixRegistrationIT`): the Kong container is skipped and etcd + `folioci/folio-apisix:latest` start instead (image is amd64-only — on arm64 rely on emulation or set `TESTCONTAINERS_APISIX_IMAGE`).
