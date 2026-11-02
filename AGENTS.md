# mgr-applications

Application manager for FOLIO Eureka: stores and validates application and module descriptors, keeps module discovery URLs, publishes discovery events to Kafka, and serves bootstrap data for module sidecars.

## Commands

```bash
mvn clean package -DskipTests  # build
mvn test                       # unit tests (@UnitTest, *Test.java)
mvn verify                     # unit and integration tests (@IntegrationTest, *IT.java); needs Docker
mvn test -Dtest=ApplicationServiceTest#create_positive  # one unit test
mvn verify -Dit.test=KongRegistrationIT -Dtest=SkipSurefire -Dsurefire.failIfNoSpecifiedTests=false  # one IT, unit tests skipped
mvn checkstyle:check           # style only
```

Integration tests use Testcontainers: `BaseIntegrationTest` starts Postgres, Kafka, and Kong; `BaseRepositoryTest` starts only Postgres.

Checkstyle also runs at `process-classes`, so every build above fails on a violation, including warnings and test sources. The base rules come from `folio-java-checkstyle`; this repository limits methods to 21 lines (`checkstyle/checkstyle-checker.properties`), and test sources are exempt from the method length check (`checkstyle/checkstyle-suppressions.xml`).

## Conventions

- The OpenAPI spec `src/main/resources/swagger.api/am.yaml` generates `org.folio.am.rest.resource` and `org.folio.am.domain.dto` in `target/generated-sources`. Change the spec, not the generated sources.
- The `moduleDescriptor` schema is mapped to the shared `org.folio.common.domain.model.ModuleDescriptor` from folio-backend-common (`importMappings` in `pom.xml`). Editing `swagger.api/schemas/module/descriptor/moduleDescriptor.json` does not change the Java model; change the model in applications-poc-tools.
- The module descriptor declares the discovery event as `mgr-applications.module.discovery.v1` (`DiscoveryPublisher.PRODUCED_EVENT_DISCOVERY`), not as the topic name `discovery`, so a topic rename does not break the declared event contract. `ModuleDescriptorTest` fails if `producesEvents` holds anything else, if the identifier does not match `<module>.<resource>.<event>.vN` or equals the topic name, if the descriptor has a `consumesEvents` key, or if `discovery` is missing from `application.kafka.producer.topics`.
- The only API gateway write is startup self-registration of this module's own service and routes, done by the `folio-integration-kong` or `folio-integration-apisix` library (selected by `APIGW_TYPE`). Change that behavior in applications-poc-tools. Application registration, deletion, and discovery changes make no gateway calls. The `KONG_*` names in `application.yml` are deprecated fallbacks for the `APIGW_*` settings.

## Gotchas

- `POST /applications` validates by default: the `check` parameter defaults to `true`. With `check=true` it runs the `VALIDATION_MODE` validators (`none`, `basic` (default), or `onCreate`) plus the `@OnCreateValidator` beans, so `ModuleDescriptorsSourceValidator` (each module has a URL or an inline descriptor, not both) runs even with `VALIDATION_MODE=none`. `check=false` skips all validation.
- Registration never checks interface dependencies. `DependenciesValidator` runs only behind `POST /applications/validate-interfaces` and `POST /applications/validate-descriptors`.
- Module descriptors given by URL are downloaded during `POST /applications`, before validation and also with `check=false`. `ModuleDescriptorLoader` does not check the HTTP status. Its `catch (IOException)` turns only I/O errors into a `ServiceException` (400); a body that is not valid JSON throws the unchecked Jackson 3 `JacksonException` and returns 500.
- Application deletion asks mgr-tenant-entitlements first. An entitled application returns 409. Any error from that call, including 404, becomes a `ServiceException` and returns 400, so deletion is blocked while mgr-tenant-entitlements is unavailable.
- `FAR_MODE=true` removes only the beans marked `@ConditionalOnFarModeDisabled`: the Kafka discovery publisher (with the outbox) and the mgr-tenant-entitlements client. Deletion then skips the entitlement check, and `POST /applications/cleanup` returns 501. Keycloak (`KC_INTEGRATION_ENABLED`) and gateway self-registration (`APIGW_ENABLED`) stay on and must be disabled separately.
- Deletion and discovery changes touch only the database. `DiscoveryPublisher` writes events to the transactional outbox table in the same transaction, so a rollback also drops the events. After commit, `TrxOutboxPollingPublisher` sends them to Kafka (also on a schedule). It ignores the future returned by `kafkaTemplate.send` and deletes the batch in the same transaction, so a delivery failure reported through that future loses the event.
- `POST /applications/cleanup` deletes each application in its own transaction. Installed applications are counted as skipped, other errors are logged and counted as failed, and the response is still 200.
- `BaseIntegrationTest` starts Kong for each subclass. Subclasses with `@EnableApisixGateway` skip Kong and start etcd and APISIX instead. The default APISIX image `folioci/folio-apisix:latest` is amd64-only: on arm64, rely on emulation or set `TESTCONTAINERS_APISIX_IMAGE`.
