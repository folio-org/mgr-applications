## v1.2.0 In progress
### Breaking changes
* Description ([ISSUE_NUMBER](https://issues.folio.org/browse/ISSUE_NUMBER))

### New APIs versions
* Provides `API_NAME vX.Y`
* Requires `API_NAME vX.Y`

### Features
* Description ([ISSUE_NUMBER](https://issues.folio.org/browse/ISSUE_NUMBER))

### Bug fixes
* Fix some bug ([ISSUE_NUMBER](https://issues.folio.org/browse/ISSUE_NUMBER))

### Tech Dept
* Description ([ISSUE_NUMBER](https://issues.folio.org/browse/ISSUE_NUMBER))

### Dependencies
* Bump `LIB_NAME` from `OLD_VERSION` to `NEW_VERSION`
* Add `LIB_NAME` `x.x.x`
* Remove `LIB_NAME`

---

## v1.1.0 2024-02-28
### New APIs versions
* Provides `applications v1.0`
* Provides `discoveries v2.0`
* Provides `module-bootstraps v1.0`

### Features
* Self-register routes in Kong ([MGRAPPS-2](https://issues.folio.org/browse/MGRAPPS-2))
* Upgrade to Keycloak 23.0.6 ([KEYCLOAK-6](https://issues.folio.org/browse/KEYCLOAK-6))
* Implement router prefix for the generated endpoints ([MGRAPPS-8](https://issues.folio.org/browse/MGRAPPS-8))

### Dependencies
* Add `org.folio:folio-integration-kong` `1.3.0`
* Add `org.folio:folio-backend-common` `1.3.0`
* Bump `org.springframework.boot:spring-boot-starter-parent` from `3.2.1` to `3.2.3`
* Bump `org.instancio:instancio-junit` from `3.7.1` to `4.3.2`
* Bump `org.openapitools:openapi-generator-maven-plugin` from `6.4.0` to `7.3.0`
* Bump `org.folio:folio-spring-cql` from `7.2.2` to `8.0.0`
* Bump `com.puppycrawl.tools:checkstyle` from `10.12.7` to `10.13.0`
* Bump `org.folio:cql2pgjson` from `35.0.4` to `35.1.2`
* Bump `org.testcontainers:testcontainers-bom` from `1.19.3` to `1.19.6`
* Bump `io.hypersistence:hypersistence-utils-hibernate-63` from `3.7.0` to `3.7.3`
* Bump `org.folio:folio-security` from `1.0.0` to `1.3.0`
* Bump `org.folio:folio-integration-kafka` from `1.0.0` to `1.3.0`
* Bump `org.folio:folio-backend-testing` from `1.0.0` to `1.3.0`

---

## v1.0.0 2024-01-22
### New APIs versions
* Provides `applications v1.0`
* Provides `discoveries v2.0`
* Provides `module-bootstraps v1.0`

### Tech Dept
* Fix maven deploy issue
* Add dependabot to track and upgrade project dependencies
* Use alpine version for postgres docker container in integration tests

### Dependencies
* Bump `org.projectlombok:lombok` from `1.18.26` to `1.18.30`
* Bump `org.springframework.boot:spring-boot-starter-parent` from `3.0.4` to `3.2.1`
* Bump `org.mapstruct:mapstruct` from `1.5.3.Final` to `1.5.5.Final`
* Bump `org.mapstruct:mapstruct-processor` from `1.5.3.Final` to `1.5.5.Final`
* Bump `com.puppycrawl.tools:checkstyle` from `10.12.3` to `10.12.7`
* Bump `org.folio:folio-spring-cql` from `7.0.0` to `7.2.2`
* Bump `io.swagger.core.v3:swagger-annotations` from `2.2.8` to `2.2.20`

