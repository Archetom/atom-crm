# B33: bounded Lead CSV export

## Scope

B33a adds only the tenant-scoped synchronous Lead CSV download that is required by the web list workflow. It is a typed Lead capability and repository path, not a reusable business export framework.

## Frozen boundary

- Route: `POST /api/v1/leads/export/csv`.
- Authorities: both `leads:read` and independent owner-derived `leads:export` must resolve for the same active tenant member.
- Request: 1–50 ordered scalar `fieldCodes`; Lead list filters `keyword`, `name`, `ownerActorId`, `subordinateOwnerOnly`, `starred`, `converted`, caller-owned `savedViewId`, or temporary conditions.
- Snapshot: read-only `REPEATABLE_READ`, a 5,001-ID probe, 200-ID projection batches, `lead_id ASC`, and 5,000-row/50-field/16-MiB hard limits.
- Output: completed UTF-8 BOM RFC 4180 CSV with CRLF, code headers and formula hardening; fixed `leads.csv`, `no-store`, and `nosniff` response headers.

## Explicit exclusions

No import protocol, generic exporter, arbitrary table/field router, asynchronous job, persisted artifact, attachment, download token, Excel-specific output, relation flattening, tenant override, caller-supplied filename or paging/sort controls is part of B33a.

## Verification

Application, persistence and REST unit tests cover field closure, scope intersection, projection cardinality, formula handling and fixed headers. `LeadIntegrationTest` runs the scope intersection through the production MySQL mappings.
