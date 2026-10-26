---
feature_id: module-bootstrap
title: Module Bootstrap (Ingress and Scoped Egress)
updated: 2026-10-26
---

# Module Bootstrap (Ingress and Scoped Egress)

## What it does

Adds two endpoints used by the FOLIO module sidecar to build its routing tables:

| Method | Path | Description |
|--------|------|-------------|
| GET | /modules/{id}/bootstrap | Ingress: the module's own discovery and provided routes only (empty `requiredModules`). |
| POST | /modules/{id}/bootstrap | Egress: discovery for the module's required/optional providers, restricted to the application scope in the request body. |

The pre-existing `GET /modules/{id}` (full, globally-resolved bootstrap) keeps its response format and follows the same
provider selection rules as egress, and its `requiredModules` are sorted the same way. Its interfaces keep the order of
the module descriptor.

## Request / response

- **Egress request** (`egressBootstrapRequest`): `{ "applicationIds": ["app-a-1.0.0", ...] }` (required, at least one id, no blank items).
- **Ingress response**: `moduleBootstrap` with `module` populated and `requiredModules: []`.
- **Egress response** (`egressBootstrap`): `{ "requiredModules": [...] }` — providers resolved within the supplied applications only; the self module is not returned. Each provider has `moduleId`, `applicationId` (the lowest supplied application that contains it), `location`, `systemUserRequired` and the `interfaces` it provides that the module requires.

## Business rules

- Egress required-module resolution only considers BACKEND providers that have a discovery URL and whose owning application is in `applicationIds`.
- If the requested module is not a BACKEND module of the supplied scope, egress returns `404`.
- Empty/missing `applicationIds`, or a null/blank item in it, returns `400`.
- Providers are filtered to the interfaces the module actually requires/optionally requires. A provider appears once, even if it provides several of them.
- One provider is kept per module name: the highest semantic version; for equal versions (build metadata is ignored) the lowest module id (same selection as `GET /modules/{id}`).
- Different modules that provide the same interface, which is the normal case for interfaces of type `multiple`, are all returned. Only versions of the same module name compete.
- A provider that belongs to several in-scope applications is reported with the lowest application id.
- The response is deterministic: `requiredModules` are sorted by `moduleId`, and the interfaces of an egress provider are sorted by interface id.
- There is no tenant context; the application scope is the only filter.

## Storage

Egress bootstrap does not read module descriptors. Each `PROVIDES` row of `module_interface_reference` keeps a copy of the
provided interface (id, version, interface type and handlers) in the `provided_interface` column. The copy is written
together with the module when an application is registered (including when a later application version re-registers an
existing module with a changed descriptor), so it stays in sync with `module.descriptor`. The column is `NULL` for
`REQUIRES` and `OPTIONAL` rows. Rows that existed before the column was added are filled by a one-time Liquibase
backfill from `module.descriptor`; a `PROVIDES` row that has no copy is skipped by egress bootstrap.

Discovery create/update/delete changes only `module.discovery_url`; the descriptor columns are not rewritten.
