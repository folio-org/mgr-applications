# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

`mgr-applications` is a Spring Boot application that serves as the Application Manager for the FOLIO library management platform. It provides functionality for managing application and module lifecycles, including registration, discovery, deployment, validation, and integration with external services.

**Technology Stack:**
- Java 21 with Spring Boot 3.5.7
- PostgreSQL database with JPA/Hibernate
- Liquibase for database migrations
- Kafka for event streaming
- Kong Gateway integration for API routing
- Keycloak for authentication/authorization
- OpenAPI code generation for REST APIs
- MapStruct for object mapping
- Lombok for boilerplate reduction

## Build and Test Commands

### Building the Project

```shell
# Full build with tests
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Run checkstyle verification
mvn checkstyle:check
```

### Running Tests

```shell
# Run unit tests only (tests tagged with @Tag("unit"))
mvn test

# Run integration tests (tests tagged with @Tag("integration"))
mvn verify

# Run a single test class
mvn test -Dtest=ApplicationServiceTest

# Run a single test method
mvn test -Dtest=ApplicationServiceTest#shouldCreateApplication
```

**Test Organization:**
- Unit tests: Tagged with `@Tag("unit")`, use `mvn test`
- Integration tests: Tagged with `@Tag("integration")`, use `mvn verify` or `mvn failsafe:integration-test`
- Integration tests are in files ending with `*IT.java`
- Unit tests are in files ending with `*Test.java`
- Test containers are used for PostgreSQL and Kafka in integration tests

### Running the Application

```shell
# Run locally (requires PostgreSQL and proper environment variables)
java \
  -Dokapi.url=http://localhost:9130 \
  -Dokapi.token=${okapiToken} \
  -jar target/mgr-applications-*.jar

# Build Docker image
docker build -t mgr-applications .
```

## Architecture Overview

### Domain Model

The application manages two primary entities with a many-to-many relationship:

**Applications (`ApplicationEntity`):**
- Contain an `ApplicationDescriptor` (stored as JSONB)
- Have multiple modules (backend and UI modules)
- Stored in `application` table

**Modules (`ModuleEntity`):**
- Have a `ModuleDescriptor` (stored as JSONB) defining routes, permissions, interfaces
- Can be BACKEND or UI type (`ModuleType` enum)
- Have interface references for dependency management (`InterfaceReferenceEntity`)
- Support discovery via `discoveryUrl` (tracked in `ModuleDiscoveryEntity`)
- Stored in `module` table
- Many-to-many relationship with applications via `application_module` join table

**Module Discovery (`ModuleDiscoveryEntity`):**
- Filtered view of modules with non-null `discovery_url`
- Uses `@SQLRestriction("discovery_url IS NOT NULL")`
- Represents where module instances are deployed/running

### Layer Architecture

```
Controllers (REST API)
    ↓
Services (Business Logic)
    ↓
Repositories (Data Access) ← Entities (Domain Model)
    ↓
Integration Layer (External Systems)
```

**Key Service Classes:**
- `ApplicationService`: Core CRUD operations for applications, version management, validation orchestration
- `ApplicationValidatorService`: Validates application descriptors
- `ApplicationDescriptorsValidationService`: Batch validation logic
- `DependenciesValidator`: Validates module dependencies and interface requirements
- `ModuleDiscoveryService`: Manages module discovery registration/updates
- `ModuleBootstrapService`: Handles module bootstrap initialization

**Mapper Pattern:**
- MapStruct interfaces in `org.folio.am.mapper` package
- `MappingMethods`: Shared mapping utilities
- Converts between DTOs (generated from OpenAPI) and domain entities

**Repository Pattern:**
- Spring Data JPA repositories in `org.folio.am.repository`
- `ApplicationRepository`: Complex queries including entity graphs for performance
- `ModuleRepository`: Module-specific queries
- `ModuleDiscoveryRepository`: Discovery-specific queries
- **CQL Support**: Repositories extend `JpaCqlRepository` for Common Query Language support
  - Enables flexible filtering via CQL queries (e.g., `name=="app*" and version=="1.0.0"`)
  - Converted to SQL via `cql2pgjson` library
  - Used in `findByQuery()` methods throughout repositories

### Integration Points

**Kong Gateway Integration (`org.folio.am.integration.kong`):**
- `KongDiscoveryListener`: Event listener that syncs discovery changes to Kong
- Automatically registers/updates Kong services and routes based on module descriptors
- Supports tenant-based routing with dynamic route expression updates
- Controlled by `KONG_INTEGRATION_ENABLED` environment variable

**Kafka Integration (`org.folio.am.integration.kafka`):**
- `DiscoveryPublisher`: Publishes discovery change events to `${ENV}.discovery` topic
- `EntitlementEventListener`: Consumes entitlement events from `${ENV}.entitlement` topic
  - Updates Kong routes with tenant restrictions when `KONG_TENANT_CHECKS_ENABLED=true`
  - Handles ENTITLE, UPGRADE, REVOKE event types
  - Dynamically adds/removes tenants from route expressions using `x-okapi-tenant` header validation

**Keycloak Integration (`org.folio.am.integration.okapi`):**
- Import mode: Creates/updates resource servers, clients, resources, permissions, roles, policies
- Security mode: JWT token validation and endpoint permission evaluation
- Controlled by `KC_INTEGRATION_ENABLED` environment variable

**Manager Tenant Entitlements Integration (`org.folio.am.integration.mte`):**
- `EntitlementService`: Checks if applications have active entitlements before deletion
- Prevents deletion of applications that are currently entitled to tenants

### Event-Driven Architecture

The application uses event listeners for cross-cutting concerns via the `ApplicationEventPublisher` service:

**Listener Interfaces:**
- `ApplicationDescriptorListener`: Interface for application lifecycle events
  - `onDescriptorCreate()`: Fired when application is created
  - `onDescriptorDelete()`: Fired when application is deleted
- `ApplicationDiscoveryListener`: Interface for discovery lifecycle events
  - `onDiscoveryCreate()`: Fired when module discovery is registered
  - `onDiscoveryUpdate()`: Fired when discovery is updated
  - `onDiscoveryDelete()`: Fired when discovery is removed

**Event Flow Example:**
```
ApplicationService.create()
  → ApplicationEventPublisher.publishDescriptorCreate()
    → OkapiModuleRegisterService (Keycloak registration)
  → ModuleDiscoveryService.addDiscoveryUrl()
    → ApplicationEventPublisher.publishDiscoveryCreate()
      → KongDiscoveryListener (Kong service creation)
      → DiscoveryPublisher (Kafka event)
```

**Outbox Pattern:**
- Transactional outbox tables for reliable event publishing
- Located in `org.folio.am.integration.messaging` package

### Code Generation

**OpenAPI Generator:**
- API specification: `src/main/resources/swagger.api/am.yaml`
- Generated code: `target/generated-sources`
- Packages:
  - DTOs: `org.folio.am.domain.dto`
  - REST interfaces: `org.folio.am.rest.resource`
- Controllers implement generated interfaces

### Validation Framework

**Validation Modes (`ValidationMode` enum):**
- `NONE`: No validation
- `BASIC`: Basic descriptor validation
- `ON_CREATE`: Full validation including dependency checks (used during creation)
- Controlled by `VALIDATION_MODE` environment variable

**Validators (`org.folio.am.service.validator`):**
- `ModuleDescriptorValidator`: Validates module descriptors
- `ModuleDescriptorsSourceValidator`: Validates descriptor sources
- `DependenciesValidator`: Validates dependencies between modules and interface availability

### Special Modes

**Folio Application Registry (FAR) Mode:**
- When `FAR_MODE=true`: Only CRUD operations for application descriptors are enabled
- Disables Kong, Kafka, Okapi, and mgr-tenant-entitlements integrations
- Used for registry-only deployments without service discovery

## Important Patterns

### Entity Relationships

- `ApplicationEntity` ↔ `ModuleEntity`: Many-to-many (applications can include multiple modules, modules can belong to multiple applications)
- `ModuleEntity` → `InterfaceReferenceEntity`: One-to-many (modules provide/require interfaces)
- Module types are split into `BACKEND` and `UI` via the `ModuleType` enum

### Database Schema

- Liquibase changelogs: `src/main/resources/changelog/`
- Master changelog: `changelog-master.xml`
- Versioned changes organized by version (v1.0.0, v3.0.0, v4.0.0)
- JSONB columns for storing descriptors (flexible schema)

### Transaction Management

- `@Transactional` on service layer
- Read-only transactions by default on service classes
- Write operations explicitly marked with `@Transactional`
- Transactional outbox pattern for reliable event publishing

### Configuration

- Spring Boot profiles and properties
- Environment variable-driven configuration (see README.md for full list)
- Conditional bean creation based on integration flags:
  - `@ConditionalOnProperty` for Kong, Keycloak, Kafka, Okapi integrations

### Annotation Processing

**Important Order Requirements:**
- Lombok must process before MapStruct
- Maven compiler plugin annotation processor paths are ordered: Lombok → Spring Boot Config Processor → MapStruct
- This ensures Lombok generates getters/setters before MapStruct generates mapping code
- IDE configuration must match this ordering

## Common Development Tasks

### Adding a New Endpoint

1. Update OpenAPI spec: `src/main/resources/swagger.api/am.yaml`
2. Run `mvn clean install` to regenerate interfaces
3. Implement the generated interface in a Controller class
4. Add service layer logic
5. Write unit tests and integration tests

### Modifying Database Schema

1. Create new Liquibase changelog XML in `src/main/resources/changelog/changes/vX.X.X/`
2. Include it in the version-specific changelog (e.g., `changelog-4.0.0.xml`)
3. Update entity classes to match schema changes
4. Test with integration tests that spin up PostgreSQL via Testcontainers

### Working with Module Descriptors

- Module descriptors define routes, permissions, and interfaces
- Stored as JSONB in the `descriptor` column of `module` table
- Use `ModuleDescriptorLoader` to load/parse descriptors
- Descriptors are from the `folio-backend-common` dependency (`org.folio.common.domain.model.ModuleDescriptor`)

### Integration Testing

- Integration tests use `@SpringBootTest` with Testcontainers
- WireMock for mocking external HTTP services (Kong, Keycloak, mgr-tenant-entitlements)
- Kafka test containers for event testing
- PostgreSQL test containers for database testing
- Use `@Tag("integration")` to mark integration tests

### Unit Testing

**IMPORTANT**: This project has comprehensive unit testing guidelines. See `https://github.com/folio-org/folio-eureka-ai-dev/blob/master/docs/testing/unit-testing.md` for detailed best practices.

**Key Unit Testing Principles:**

1. **Never use lenient mode** - `@MockitoSettings(strictness = Strictness.LENIENT)` is forbidden
2. **Only stub what you need** - Avoid unnecessary stubbing in `@BeforeEach`, use helper methods instead
3. **Only verify unmocked interactions** - Don't verify methods already mocked with `when()`
4. **Test naming convention**: `methodName_scenario_expectedBehavior`
   - Example: `tableExists_positive_tableIsPresent`
   - Example: `deleteTimer_negative_timerDescriptorIdIsNull`
5. **Test structure**: Arrange-Act-Assert pattern
6. **Resource cleanup**: Always verify that connections, streams, etc. are closed
7. **Test independence**: Each test should be self-contained and not rely on execution order
8. **Helper methods**: Use private helper methods for common mock setups (e.g., `setupContextMocks()`)
9. **Parameterized tests**: Use `Stream<Type>` directly for single parameter tests, not `Stream<Arguments>`
10. **Explicit lambda types**: Declare types explicitly when var inference fails (e.g., `Supplier<RuntimeException>`)

**Example Test Structure:**
```java
@UnitTest
@ExtendWith(MockitoExtension.class)
class MyServiceTest {
    @Mock
    private Dependency dependency;

    private void setupCommonMocks() {
        when(dependency.getSomething()).thenReturn("value");
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(dependency); // Only include mocks that should be fully accounted for
    }

    @Test
    void operation_positive_success() {
        setupCommonMocks(); // Only call if needed
        var service = new MyService(dependency);

        when(dependency.doSomething()).thenReturn(expectedValue);

        var result = service.operation();

        assertThat(result).isEqualTo(expectedValue);
        verify(dependency).close(); // Only verify unmocked calls
    }
}
```

**Important Verification Rules:**
- ❌ DON'T verify mocked methods: `when(foo.bar()).thenReturn(x); verify(foo).bar();` is redundant
- ✅ DO verify unmocked resource cleanup: `verify(connection).close();`
- ✅ DO verify unmocked side effects: Methods called but not stubbed with `when()`

For complete guidelines, examples, and checklist, refer to the external documentation at `https://github.com/folio-org/folio-eureka-ai-dev/blob/master/docs/testing/unit-testing.md`.

## Key Dependencies

- `folio-spring-cql`: CQL query support for FOLIO
- `folio-security`: Security and Keycloak integration
- `folio-integration-kafka`: Kafka integration utilities
- `folio-backend-common`: Common FOLIO backend models (ModuleDescriptor)
- `folio-integration-kong`: Kong Gateway integration
- `cql2pgjson`: CQL to PostgreSQL JSON conversion
- `semver4j`: Semantic versioning support

## Notes

- The application uses Log4j2 for logging (Spring Boot starter-logging is excluded)
- Checkstyle is enforced during build with FOLIO rules
- Lombok is used extensively - ensure annotation processing is enabled in your IDE
- MapStruct annotation processors must run after Lombok
- The application supports SSL/TLS configuration for server and client connections
- Secure storage integrations: AWS-SSM, Vault, FSSP (Folio Secure Store Proxy)
