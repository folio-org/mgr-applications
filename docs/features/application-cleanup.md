---
feature_id: application-cleanup
title: Application Cleanup
updated: 2026-04-14
---

# Application Cleanup

## What it does
Application Cleanup provides a bulk `POST /applications/cleanup` operation that inspects registered application descriptors and removes the ones that are not currently installed for any tenants. The endpoint returns a best-effort summary showing how many application descriptors were inspected, removed, skipped, or failed, plus the IDs in each category.

## Why it exists
Operators need a safe way to remove stale application descriptors without manually checking each application first. This feature reduces cleanup work while protecting application descriptors that are still actively installed for tenants.

## Entry point(s)
| Method | Path | Description |
|--------|------|-------------|
| POST | `/applications/cleanup` | Removes application descriptors that are not installed for any tenants and returns a cleanup summary |

## Business rules and constraints
- The operation inspects every application descriptor ID currently stored in the `application` table.
- Cleanup is best-effort: one deletion failure does not stop later application descriptors from being processed.
- Application descriptors that still have tenant entitlements are skipped instead of being deleted.
- The response always includes counts and ID lists for `cleaned`, `skipped`, and `failed` results.
- Cleanup reuses the normal application deletion flow, so modules shared with other applications are retained while modules used only by the deleted application can be removed together with their discovery records.

## Error behavior
- `200 OK` is returned even when some application descriptors cannot be removed; those IDs are reported in `failedIds`.
- Application descriptors that are still installed are not returned as HTTP conflicts during bulk cleanup; they are counted in `skipped` and listed in `skippedIds`.
- `501 Not Implemented` is returned when the entitlement service is unavailable, including when FAR mode disables that integration.
- `500 Internal Server Error` can still occur for request-level failures such as being unable to load application IDs before cleanup starts or other uncaught server errors.

## Configuration
| Variable | Purpose |
|----------|---------|
| `application.far-mode.enabled` | When `true`, disables the tenant-entitlement integration that cleanup depends on, so the endpoint returns `501 Not Implemented`. Maps to `FAR_MODE`. |
| `tenant.entitlement.url` | Base URL for the tenant-entitlement service queried to determine whether an application is still installed. Maps to `TE_URL`. |
| `tenant.entitlement.tls.enabled` | Enables TLS for tenant-entitlement requests used during cleanup. Maps to `TE_TLS_ENABLED`. |

## Dependencies and interactions
- Depends on `mgr-tenant-entitlements` `GET /entitlements` queries filtered by `applicationId=<id>` to determine whether an application descriptor can be deleted.
- When `application.okapi.enabled` is enabled, removing an unused application descriptor also deletes Okapi module descriptors that are no longer referenced and removes Okapi discovery records for deleted module instances.
- When `application.kong.enabled` is enabled and a removed backend module has discovery information, cleanup-triggered discovery deletion removes the corresponding Kong service.
- When both `application.kong.enabled` and `routemanagement.enable` are enabled, the same cleanup flow also deletes that Kong service's routes before deleting the service.
