---
feature_id: kong-route-management
title: Kong Gateway Route Management
updated: 2026-02-10
---

## What it does

Automatically creates, updates, and deletes Kong Gateway routes when module discovery information changes. When a backend module registers its location (discovery information), the service configures Kong Gateway to route traffic to that module based on the routes defined in the module's descriptor.

## Why it exists

Kong Gateway acts as the API gateway/reverse proxy for the FOLIO platform. When a module instance starts and registers its discovery information (hostname/port), Kong needs to be configured with:
- A service pointing to the module's location
- Routes mapping API paths to that service

Without this automation, operators would need to manually configure Kong whenever modules are deployed, updated, or removed. This feature ensures Kong's routing configuration stays synchronized with the actual deployed module topology.

## Entry point(s)

**REST API:**
- `POST /modules/{id}/discovery` - creates discovery information and triggers Kong service/route creation
- `PUT /modules/{id}/discovery` - updates discovery information and reconfigures Kong routes
- `DELETE /modules/{id}/discovery` - removes discovery information and deletes Kong routes/service
- `POST /modules/discovery` - batch creates discovery information for multiple modules

**Internal events:**
Discovery lifecycle events are published via `ApplicationEventPublisher` to all registered `ApplicationDiscoveryListener` implementations. The `KongDiscoveryListener` listens for:
- `onDiscoveryCreate` - creates Kong service and routes
- `onDiscoveryUpdate` - recreates Kong routes for updated module
- `onDiscoveryDelete` - removes Kong routes and service

## Business rules and constraints

- **UI modules are excluded**: Only backend modules (BE modules) are registered with Kong. UI modules (type = `ModuleType.UI`) are ignored.

- **Route source**: Routes are read from the module's stored descriptor (from the module repository), not from the discovery request payload.

- **Update behavior**: When discovery information is updated, existing Kong routes are deleted first, then new routes are created based on the module descriptor. This ensures stale routes are removed.

- **Delete behavior**: On discovery deletion, routes are deleted before the Kong service is removed.

- **Service naming**: The Kong service name and ID match the module's discovery ID (typically `{moduleId}-{instanceId}`).

- **Service URL**: The Kong service URL is set to the module's discovery location (e.g., `http://module-host:8081`).

## Error behavior

- **Service doesn't exist on delete**: If Kong returns `NoSuchElementException` when deleting routes, the error is caught and logged as debug (not an error), because a non-existent service has no routes to clean up.

- **Module not found**: If the module referenced in discovery create/update doesn't exist in the repository, the operation fails with an exception.

- **Discovery already exists**: Attempting to create discovery for a module that already has discovery information fails with `EntityExistsException`.

## Configuration

| Variable | Purpose |
|----------|---------|
| `application.kong.enabled` | Enables or disables Kong integration entirely. When `false`, `KongDiscoveryListener` is not loaded. Maps to env var `KONG_INTEGRATION_ENABLED` (default: `true`). |
| `routemanagement.enable` | Controls whether routes are managed in Kong. When `false`, only Kong services are created/deleted, but routes are not added. Maps to env var `ROUTEMANAGEMENT_ENABLE` (default: `true`). |
| `application.kong.url` | Kong Admin API URL. Maps to env var `KONG_ADMIN_URL` (falls back to `kong.url`). |
| `application.kong.connect-timeout` | Kong client connection timeout. Maps to env var `KONG_CONNECT_TIMEOUT`. |
| `application.kong.read-timeout` | Kong client read timeout. Maps to env var `KONG_READ_TIMEOUT`. |
| `application.kong.write-timeout` | Kong client write timeout. Maps to env var `KONG_WRITE_TIMEOUT`. |
| `application.kong.retries` | Number of Kong client retries. Maps to env var `KONG_RETRIES`. |

## Dependencies and interactions

**Depends on: Kong Gateway (external)**
- Uses Kong Admin API to create/update/delete services and routes
- Kong service operations: upsert service by ID, delete service by ID
- Kong route operations: add routes from module descriptor, delete all routes for a service

**Depends on: Module Repository (internal)**
- Reads module descriptor to extract route definitions
- Module descriptor contains the routing paths and configurations that are registered with Kong
