## v1.4.0 2024-09-30

* APPPOCTOOL-28 Use folio-auth-openid library for JWT validation (#120) ([APPPOCTOOL-28](https://issues.folio.org/browse/APPPOCTOOL-28))

---

## v1.3.2 2024-07-10

* KONG-10: upgrade kong version (#93) ([KONG-10](https://issues.folio.org/browse/KONG-10))

---

## v1.3.1 2024-06-20

* Add KC trust-store-type to application settings (#81)
* RANCHER-1502: Unify custom EUREKA related docker images building process - shared lib for kong, keycloak & sidecar (#85) ([RANCHER-1502](https://issues.folio.org/browse/RANCHER-1502))

---

## v1.3.0 2024-05-25

* MGRAPPS-12 Create a docker file for the mgr-applications module that is based on the FIPS-140-2 compliant base image (ubi9/openjdk-17-runtime) (#77)  ([MGRAPPS-12](https://issues.folio.org/browse/MGRAPPS-12))
* MGRAPPS-10: Secure mgr-applications HTTP end-points with SSL (#76) ([MGRAPPS-10](https://issues.folio.org/browse/MGRAPPS-10))
* MGRAPPS-18: add HTTPS access to mgr-tenant-entitlements ([MGRAPPS-18](https://issues.folio.org/browse/MGRAPPS-18))
* MGRAPPS-13: add HTTPS access to Kong (#69) ([MGRAPPS-13](https://issues.folio.org/browse/MGRAPPS-13))

---

## v1.2.0 2024-04-16

* MGRAPPS-11: desired permission in bootstrap data (#59) ([MGRAPPS-11](https://issues.folio.org/browse/MGRAPPS-11))
* APPPOCTOOL-10: adjust test with Keycloak test container (#55) ([APPPOCTOOL-10](https://issues.folio.org/browse/APPPOCTOOL-10))
* KONG-6: Kong timeouts should be extended (#52) ([KONG-6](https://issues.folio.org/browse/KONG-6))

---

## v1.1.0 2024-02-28
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

