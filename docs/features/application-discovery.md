---
feature_id: application-discovery
title: Application Discovery
updated: 2026-02-20
---

## What it does

Application Discovery provides read-only REST endpoints for querying module deployment locations (discovery URLs) organized by application. Consumers can retrieve discovery information for a specific application by ID, or search across multiple applications using CQL queries to find where application modules are deployed and how to reach them.

## Why it exists

External systems need to locate deployed module instances that comprise applications without loading full module descriptors or making multiple API calls. This feature enables efficient batch queries for deployment information across applications, supporting service discovery, health monitoring, and routing configuration use cases.

## Entry point(s)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/applications/{id}/discovery` | Returns paginated module discovery information (id, name, version, location) for a single application |
| GET | `/applications/discovery` | Returns discovery information grouped by application, optionally filtered by CQL query (e.g., `name=="app*"`) with pagination at application level |

**Source**: `src/main/resources/swagger.api/am.yaml`

## Business rules and constraints

- Only modules with non-null `discovery_url` values are included in results.
- Only applications containing at least one module with discovery information appear in search results.
- CQL queries in `/applications/discovery` filter at the application level; all modules of matching applications are returned regardless of limit parameter.
- Pagination parameters: `offset` (default 0, min 0), `limit` (default 10, min 0, max 500).
- The `/applications/{id}/discovery` endpoint paginates module results; `/applications/discovery` paginates application results.
- Discovery data is read-only through these endpoints; modifications occur via separate module discovery management endpoints.
- Application ID format follows `{name}-{version}` pattern (e.g., `app-one-1.0.0`).
- Results from `/applications/discovery` exclude the full ModuleDescriptor JSONB, returning only essential discovery fields for performance.

## Error behavior

- **400 Bad Request**: Returned when CQL query syntax is invalid or pagination parameters violate constraints (offset < 0 or limit > 500).
- **404 Not Found**: Returned from `/applications/{id}/discovery` when application ID does not exist or application has no modules with discovery information.
- **500 Internal Server Error**: Returned when database queries fail or unexpected server errors occur.

## Configuration

No feature-specific configuration properties. The feature uses standard database connection settings and inherits CQL query capabilities from the `JpaCqlRepository` infrastructure.

## Dependencies and interactions

**Database Views**: Uses `v_application_with_discovery` view (filters applications with at least one discovered module) and queries `module` table with `discovery_url IS NOT NULL` restriction.

**CQL Query Engine**: Leverages `JpaCqlRepository` from `folio-spring-cql` dependency to translate CQL expressions into SQL queries against the application discovery view.

**Performance Optimization**: Implements `ApplicationModuleDiscoveryProjection` interface to fetch minimal columns (`application_id, id, name, version, location`) via native SQL, avoiding full `ModuleDescriptor` JSONB loading.

**Database Indexes**: Queries benefit from `idx_application_module_appid` and `idx_application_module_moduleid` indexes on the `application_module` join table for efficient many-to-many lookups.
