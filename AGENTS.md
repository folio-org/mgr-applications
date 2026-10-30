# mgr-applications

Spring Boot Application Manager for FOLIO: manages application/module lifecycle (registration, discovery, validation). Java 21, PostgreSQL/JPA, Liquibase, Kafka, API Gateway (Kong or APISIX), Keycloak, OpenAPI codegen, MapStruct, Lombok.

## Build & Test

```bash
mvn clean install              # full build
mvn clean install -DskipTests  # skip tests
mvn test                       # unit tests (@UnitTest, *Test.java)
mvn verify                     # unit + integration tests (@IntegrationTest, *IT.java; Testcontainers Postgres+Kafka+Kong; APISIX ITs use etcd+APISIX)
mvn test -Dtest=ApplicationServiceTest#create_positive  # single unit test
mvn verify -Dit.test=KongRegistrationIT -Dtest=SkipSurefire -Dsurefire.failIfNoSpecifiedTests=false  # single IT, skips unit tests
mvn checkstyle:check           # runs during build, FOLIO rules
```

Run locally: `java -jar target/mgr-applications-*.jar`. Full env vars in `README.md`.

## Style

Match the repository's existing style: use maintained implementations and tests as templates for new code, and keep the structure, naming, and conventions of neighboring files. Mockito is never lenient. Unit-testing guide: https://github.com/folio-org/folio-eureka-ai-dev/blob/master/docs/testing/unit-testing.md

## Architecture notes

- Repositories extend `JpaCqlRepository` for CQL filtering (e.g. `name=="app*"`) via `cql2pgjson`; the query method is `findByCql()`.
- Kafka (`integration.kafka`): `DiscoveryPublisher` publishes `${ENV}.discovery` events. The module descriptor declares this under `producesEvents` as the event identifier `mgr-applications.module.discovery.v1` and declares no `consumesEvents`; `ModuleDescriptorTest` guards both. Keep the identifier independent of the topic name, so renaming a topic does not break the declared event contract.
- mgr-tenant-entitlements (`integration.mte`): blocks deletion of entitled applications.

## Pitfalls

- **FAR mode** (`FAR_MODE=true`) runs the module as a descriptor registry: it disables the Kafka discovery publisher and the mgr-tenant-entitlements client (`@ConditionalOnFarModeDisabled`), so application cleanup is unavailable and deletion skips the entitlement check. Keycloak and gateway self-registration are *not* disabled by FAR mode; set `KC_INTEGRATION_ENABLED=false` and `APIGW_ENABLED=false` explicitly to turn them off.
- **Validation** (`VALIDATION_MODE`, default `basic`): `none` / `basic` (`@BasicValidator` beans) / `onCreate` (`@OnCreateValidator` beans: each module has a URL or a descriptor, not both). `POST /applications?check=true` runs the default mode plus `onCreate`. Dependency checks are separate: `DependenciesValidator`, behind `POST /applications/validate-interfaces` and `POST /applications/validate-descriptors`.
- Legacy `KONG_INTEGRATION_ENABLED` / `REGISTER_MODULE_IN_KONG` still bind but are deprecated; use `APIGW_*` names.
- Do not edit `target/generated-sources` (OpenAPI codegen from `src/main/resources/swagger.api/am.yaml`; add endpoint: edit spec → `mvn clean install` → implement the generated interface).
- Liquibase (`src/main/resources/changelog/`): add new versioned changesets, never edit applied ones. Descriptors are stored as JSONB.
- Annotation order in DI: Lombok → Spring Config Processor → MapStruct (must match in the IDE too).
- Logging is Log4j2 (Spring default logging excluded). Secure stores: AWS-SSM/Vault/FSSP.

## Testing

Gateway ITs: `BaseIntegrationTest` starts Kong via `@EnableKongGateway` (`KongRegistrationIT`). APISIX ITs add `@EnableApisixGateway` (`ApisixRegistrationIT`): the Kong container is skipped and etcd + `folioci/folio-apisix:latest` start instead (image is amd64-only; on arm64 rely on emulation or set `TESTCONTAINERS_APISIX_IMAGE`).
