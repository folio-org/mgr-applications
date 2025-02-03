## Version `v2.0.2` (03.02.2025)
* Added module route management based on discovery information (MGRAPPS-35)

## Version `v2.0.1` (30.01.2025)
* Bump application-poc-tools version to update Keycloak (MGRAPPS-37)
---

## Version `v2.0.0` (01.11.2024)
* Increase version of keycloak-admin-client to v25.0.6 (KEYCLOAK-24)

---

## Version `v1.4.0` (30.09.2024)
* Use folio-auth-openid library for JWT validation (APPPOCTOOL-28)

---

## Version `v1.3.2` (10.07.2024)
* upgrade kong version (KONG-10)

---

## Version `v1.3.1` (20.06.2024)
* Add KC trust-store-type to application settings
* Unify custom EUREKA related docker images building process - shared lib for kong, keycloak & sidecar (RANCHER-1502)

---

## Version `v1.3.0` (25.05.2024)
* Create a docker file for the mgr-applications module that is based on the FIPS-140-2 compliant base image (ubi9/openjdk-17-runtime) (MGRAPPS-12)
* Secure mgr-applications HTTP end-points with SSL (MGRAPPS-10)
* add HTTPS access to mgr-tenant-entitlements (MGRAPPS-18)
* add HTTPS access to Kong (MGRAPPS-13)

---

## Version `v1.2.0` (16.04.2024)
* desired permission in bootstrap data (MGRAPPS-11)
* adjust test with Keycloak test container (APPPOCTOOL-10)
* Kong timeouts should be extended (KONG-6)

---

## Version `v1.1.0` (28.02.2024)
### Features
* Self-register routes in Kong (MGRAPPS-2)
* Upgrade to Keycloak 23.0.6 (KEYCLOAK-6)
* Implement router prefix for the generated endpoints (MGRAPPS-8)

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

## Version `v1.0.0` (22.01.2024)
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
