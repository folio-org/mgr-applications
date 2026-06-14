---
feature_id: module-bootstrap
title: Module Bootstrap (Ingress and Scoped Egress)
updated: 2026-06-14
---

# Module Bootstrap (Ingress and Scoped Egress)

## What it does

Exposes `POST /modules/{id}/bootstrap`, which returns routing bootstrap information for a single module in one of two flavours selected by the request body:

- **ingress** — the module's own bootstrap (its discovery/location and the routes it provides), independent of any application scope.
- **egress** — bootstrap for the required modules the module calls, restricted to the providers found within a caller-supplied set of application IDs.

A consumer (the FOLIO module sidecar) calls this endpoint at startup and whenever its tenant's installed applications change, to learn where to route its own inbound traffic (ingress) and its outbound service-to-service traffic (egress).

## Why it exists

Different tenants can have different applications — and therefore different provider module versions — installed. A single tenant-independent bootstrap cannot express "for this tenant's application set, which provider version satisfies each required interface." This endpoint lets a caller pass the application scope explicitly and receive the egress bootstrap resolved against exactly that scope, while still supporting a scope-free ingress lookup for the module's own routes.

It complements the existing `GET /modules/{id}/bootstrap`, which always resolves required modules globally. The `POST` variant separates the ingress and egress concerns and makes egress resolution scope-aware.

## Entry point(s)

| Method | Path | Description |
|--------|------|-------------|
| POST | /modules/{id}/bootstrap | Returns ingress (scope-independent) or egress (application-scoped) bootstrap for the module |

**Request body** (`moduleBootstrapRequest`):

| Field | Required | Purpose |
|-------|----------|---------|
| `type` | yes | `ingress` or `egress` |
| `applicationIds` | egress only | Application IDs that define the egress resolution scope |

**Response body** (`moduleBootstrapResponse`):

- `type=ingress` → `ingress` is populated (a `moduleBootstrap`: `module` + empty `requiredModules`); `egress` is null.
- `type=egress` → `egress` is populated (`egressBootstrapResult`: `found` plus, when found, `bootstrap`); `ingress` is null.

## Business rules and constraints

- **Ingress is scope-independent.** `type=ingress` returns only the module's own discovery (`module`) with an empty `requiredModules` list. It does not resolve dependencies.
- **Egress requires a scope.** When `type=egress` and `applicationIds` is null or empty, the result is `found=false` with no bootstrap.
- **Egress is restricted to the supplied applications.** Required-module resolution only considers provider modules whose `applicationId` is in `applicationIds`. If the requested module itself is not present in that scope, the result is `found=false`.
- **Required modules are filtered to actually-used interfaces.** Only interfaces listed in the module descriptor's `requires`/`optional` sections are kept on the returned provider discoveries; unused provided interfaces are stripped.
- **Required modules are deduplicated by name, keeping the highest version.** When multiple versions of the same provider module are in scope, only the highest version (per interface-version comparison) is returned.
- **Providers must have a discovery location.** A required/provider module is only included if it has a registered discovery location; the requested module itself is always included.

## Error behavior

- **400 Bad Request** — invalid request body (e.g. missing `type`).
- **404 Not Found** — `type=ingress` for a module id that does not exist.
- **Egress, module not in scope** — returns **200** with `egress.found=false` (not a 404).
- **500 Internal Server Error** — unexpected failure.

## Dependencies and interactions

- **PostgreSQL (internal).** Resolution reads the module and its dependency graph via the `ModuleBootstrapView` projection and `InterfaceReferenceEntity` (`REQUIRES`/`OPTIONAL`/`PROVIDES`). The egress query additionally constrains providers to `applicationId IN (:applicationIds)`.
- **Consumed by: folio-module-sidecar.** The sidecar calls `type=ingress` to build its own inbound routes and `type=egress` (with the tenant's application scope) to build per-tenant egress route tables. See the sidecar's `tenant-scoped-egress-routing` feature.
