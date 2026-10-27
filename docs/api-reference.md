# HTTP API reference

This page documents the bundled User example, local organization, tenant authorization, navigation, customization, CRM record, enterprise-data lookup, product-catalog, workflow, and region reference-data contracts. OpenAPI JSON is available at `/v3/api-docs` and Swagger UI at `/swagger-ui/index.html` in development. Production disables both by default.

## Authentication

All `/api/v1` operations require an authenticated principal and a tenant unless explicitly documented as public. The development adapter accepts `X-Dev-User-Id` and `X-Dev-Tenant-Id` only when the `dev` or `test` profile is active and `ATOM_SECURITY_TRUSTED_HEADER_ENABLED=true`.

These headers are development credentials, not a production authentication protocol. See [configuration](configuration.md) for production authentication requirements.

### Browser password session and recovery

The public password flow is deliberately two-step: `POST /api/v1/authentication/password/login-options` receives `PasswordLoginOptionsRequest` and returns `PasswordLoginOptionsResponse`, containing an opaque short-lived `loginChallenge` and only the verified user's active `PasswordLoginTenantResponse` choices. `POST /api/v1/authentication/password/sessions` consumes `PasswordSessionCreateRequest` for one of those choices, returns `AuthenticationSessionResponse`, and sets the HttpOnly session/CSRF cookies. `GET /api/v1/authentication/session` returns that safe session projection or `401` and clears invalid cookies; `DELETE /api/v1/authentication/session` ends it.

`POST /api/v1/authentication/password/reset-requests` accepts tenant-scoped `PasswordResetRequest` and returns `202` without revealing whether an account exists. It returns stable `503` only when delivery cannot be attempted. `POST /api/v1/authentication/password/reset-confirmations` consumes `PasswordResetConfirmRequest` once and returns `204`; invalid, consumed, or expired tokens are all `401`. Clients must generate these schemas from OpenAPI, must not render server `errMsg`, and must not persist passwords, reset tokens, challenges, or session values.

### First installation

`GET /api/v1/installation` is public and returns the typed `InitialInstallationStatusResponse`. It exposes only `installationRequired`; it returns `false` once initialization is claimed by an active login user holding an active `TENANT_OWNER` membership. Historical tenant, membership or role fixtures without a corresponding active login user do not close the route. On a fresh system, `POST /api/v1/installation/tenant-admin` accepts `InitialTenantAdminRequest` (`displayName`, `loginName`, `email`, and a 12–64 character password) and atomically provisions tenant 1 with its owner administrator and default configuration. On success it returns `AuthenticationSessionResponse` and sets the same HttpOnly session/CSRF cookies as password login. It accepts no tenant ID, role, authority, or identity header. A concurrent or subsequent setup command returns stable `409 RESOURCE_ALREADY_EXISTS`; clients should direct the user to normal login and must not display server `errMsg`. There are no default credentials.

## Operations

### Typed response schemas

The OpenAPI 3.1 document declares successful management and read responses explicitly;
clients must generate from that document rather than infer a response from an untyped `object`.
Dashboard overview, preferences, and member target management use `DashboardOverviewResponse`,
`DashboardPreferenceResponse`, `PerformanceTargetResponse`, and
`PerformanceTargetListResponse`. Report routes use their corresponding
`CoreReportResponse`, trend/ranking/catalog/definition/run responses, and the definition query
uses `ReportDefinitionPageResponse`. OA form, request, managed-announcement, announcement-inbox,
marketing-form, and marketing-submission queries use their corresponding typed `*PageResponse`;
each page has `pageNum`, `pageSize`, `totalNum`, and an `objectList` whose item type is also
declared. Connector control-plane reads use `ConnectorResponse`, query uses
`ConnectorPageResponse`, and the one-time credential boundary uses
`ConnectorCredentialCreatedResponse` only for create and rotation. Detail and mutation routes use
their specific `*Response` type, while the OA available
catalog is an explicitly typed array. This preserves the runtime JSON shape while making every
success payload safely available to generated clients.

### Dashboard sales brief

`GET /api/v1/dashboard/overview` returns the fixed `salesBrief` array in the same read snapshot as
its KPI cards, rankings, and target. The array has nine server-ordered source metrics:
`NEW_CUSTOMER`, `NEW_CONTACT`, `NEW_OPPORTUNITY`, `NEW_CONTRACT`, `CONTRACT_AMOUNT`,
`OPPORTUNITY_AMOUNT`, `RECEIVABLE_AMOUNT`, `NEW_LEAD`, and `NEW_FOLLOW_UP`. Each entry contains a `COUNT` or
`AMOUNT` unit, the selected-range `value`, and, when the calendar window has a comparable prior
range, the server-calculated percentage `comparisonRate`. Each source independently intersects
the caller's object read authority, effective data scope, and required unmasked fields. A denied
source is returned as `UNAVAILABLE` with no value, prior value, or comparison rate; a client must
not infer a zero or calculate a replacement comparison. `NEW_FOLLOW_UP` is deliberately limited
to the current member's active, user-created follow-up records (`activities:read` plus active
membership and `SELF` dashboard scope); broader dashboard scopes return it as unavailable rather
than assembling a cross-subject aggregate.

### Dashboard sales funnel

The same `GET /api/v1/dashboard/overview` response has a typed `salesFunnel` projection. Its
`AVAILABLE` state contains server-ordered active `pipelines`, each with server-ordered stages,
stage `probability`, and the caller-visible selected-window `count` and `amount`; active catalog
stages are retained with zero values when no visible record is in that stage. The projection
requires `opportunity-pipelines:read`, `opportunities:read`, the current opportunity visibility,
and readable unmasked created time, status, pipeline, stage, and estimated amount fields. A known
access denial is `UNAVAILABLE` with no pipelines, while an active catalog with no stages is
`EMPTY`. The dashboard preference only narrows this result. Clients may choose a returned pipeline
or render count versus amount locally, but must not use that choice to request, infer, or construct
a broader aggregation.

### Dashboard customer contact reminder

The same `GET /api/v1/dashboard/overview` response has a required
`customerContactReminder` projection with `AVAILABLE` or `UNAVAILABLE` status. `AVAILABLE`
contains the fixed server order `OVER_7_DAYS`, `OVER_15_DAYS`, `OVER_30_DAYS`,
`OVER_3_MONTHS`, `OVER_6_MONTHS`, and `OVERDUE`, each with a non-negative cumulative count.
The projection requires current `customers:read`, `activities:read`, active membership, caller
visible active customers, and readable unmasked customer created-time and status fields; the saved
dashboard scope only narrows that customer visibility. The first five counts compare the newest
active `CUSTOMER/FOLLOW_UP` occurrence to the request's fixed UTC timestamp, falling back to the
customer creation timestamp when no such follow-up exists. `OVERDUE` is based only on the newest
active customer follow-up's past `next_contact_time`; a later follow-up prevents an earlier plan
from remaining overdue. A known access denial is `UNAVAILABLE` with no buckets. Clients must not
derive, supplement, or expose customer-level activity information from this summary.

### CRM CSV import drafts

`/api/v1/crm-import-drafts` stages a private `LEAD`, `CONTACT`, or `PRODUCT` CSV before any
records are written. Template, history, preview, confirm, and withdrawal all recheck the current
member, tenant and object authority; source attachment creation additionally requires
`files:write`. Preview returns only a bounded valid-row count and `rowNumber`/stable `errCode`
pairs. When a still-active preview has issues, `GET /api/v1/crm-import-drafts/{draftId}/error-report`
reparses the same controlled attachment and returns a UTF-8 BOM CSV with exactly
`rowNumber,errCode`. It requires the same uploader and current object/files write access, returns
`Cache-Control: no-store` and `X-Content-Type-Options: nosniff`, uses the fixed attachment name
`crm-import-errors.csv`, and rejects a ready draft with no parser issues. It never returns source
bytes, source filename or cell values. Unconfirmed source bytes and the corresponding error-report
eligibility expire after seven days. A bounded internal cleanup worker marks the draft expired,
withdraws the private attachment relation and schedules the existing controlled byte-reclamation
path; only non-sensitive lifecycle metadata remains for audit.

`GET /api/v1/crm-import-drafts/history/{objectType}` pages that history with the same
`pageNum`/`pageSize`/`totalNum`/`objectList` fields as every other paged response. It previously
published `page`/`size`/`total`/`items`, which were the internal value object's names rather than
the API's; callers reading the old names must move to the shared ones.

### Marketing activities

`/api/v1/marketing-activities` is the tenant-scoped campaign-management boundary observed in
the CRM market-activity workbench. It has no public submission, field-configuration or
recipient-directory route: those workflow surfaces remain explicitly separate until their full
server contracts are introduced. The persisted core only accepts a name, a closed related-object
type (`CUSTOMER`, `CONTACT`, or `LEAD`), active tenant-member and/or active tenant-department
participant IDs, time range, and the optional type, non-negative `viewCount`/`submissionCount`,
budget, address, description and single-submission flag. An activity can reference one existing,
non-deleted marketing form from the same tenant through `marketingFormId`, or the create route can
accept exactly one nested `marketingForm` request to create a `LEAD` or `CUSTOMER` capture form and
link it in the same transaction. The form target must match the activity related-object type;
`CONTACT` activities cannot carry a marketing form. These inputs are mutually exclusive; nested
form creation is only available during activity creation, never update, and it does not activate
the resulting form.
Both counters default to `0`. Creation, updates and deletion always recheck every submitted
member, department and form reference; list and detail responses disclose only the activity's own
bounded fields, participant IDs, form ID and (only when the associated form is active) its
canonical `marketingFormPublicId`.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `GET /api/v1/marketing-activities/configuration-options` | `marketing-activities:read` | Read the closed server-defined activity-type catalog for creation and edit forms. |
| `GET /api/v1/marketing-activities?page=1&size=20&keyword=…&relatedObjectType=CUSTOMER` | `marketing-activities:read` | Query active tenant activities by name and optional related-object type. |
| `GET /api/v1/marketing-activities/{activityId}` | `marketing-activities:read` | Read one active tenant activity. |
| `POST /api/v1/marketing-activities` | `marketing-activities:write` | Create one activity; every submitted member/department must be active in the tenant. Associating an existing form requires `marketing-forms:read` and a matching `LEAD` or `CUSTOMER` related-object type; nested `marketingForm` instead requires `marketing-forms:write` and atomically creates an inactive matching form. |
| `POST /api/v1/marketing-activities/batch-delete` | `marketing-activities:delete` | Atomically soft-delete 1–100 unique `activityId`/current-`version` pairs. The command locks and validates every target before deleting any activity or withdrawing its attachments. |
| `PUT /api/v1/marketing-activities/{activityId}` | `marketing-activities:write` | Replace the activity's bounded core fields with its current `version`; changing or retaining a form association also requires `marketing-forms:read`. Nested `marketingForm` is rejected. |
| `DELETE /api/v1/marketing-activities/{activityId}?version=…` | `marketing-activities:delete` | Soft-delete an activity using its current version. |

All successful list and detail results use `MarketingActivityPageResponse` and
`MarketingActivityResponse`; the latter includes `participantActorIds`,
`participantDepartmentIds`, optional `marketingFormId`/`marketingFormPublicId`, `viewCount`,
`submissionCount`, `status`, `version` and timestamps. The public ID is omitted while an
associated form is inactive, so activities never publish a guessed or stale public route. All
unset optional activity properties are omitted from JSON rather than serialized as `null`, which
keeps the response aligned with the generated OpenAPI optional-property contract.
The configuration response returns stable `code`/`displayName` pairs. New create/update requests
may use only a current code; a historical stored type is accepted only when an edit preserves that
same exact value, allowing records from before the catalog to remain editable without making the
catalog an open-ended text input.
Deleted activities are not queryable. The database enforces tenant-scoped foreign keys for the
creator, every participant member, every participant department and the optional marketing form,
the closed object-type/status sets, non-negative counters and budget, `startTime <= endTime`,
and optimistic-lock versioning.

### Marketing activity image attachments

The closed attachment target token `marketing-activity` uses the standard controlled route
`/api/v1/attachment-targets/marketing-activity/{activityId}/attachments`. It remains private:
all reads require `files:read + marketing-activities:read`, and intent creation, upload-status
and withdrawal require `files:write + marketing-activities:write`; the activity and membership
are rechecked at every boundary. There is no public object-store URL, direct byte upload, or
browser-selected target identity.

An upload intent may declare `marketingActivityImageType` as `COVER` or `DETAIL` only for this
target. Such intents accept PNG/JPEG declarations only, and the server counts both uploading and
published active relations while the activity root is locked, limiting each group to nine files.
The optional same-named list query parameter returns only the requested group; omitting it keeps
the ordinary complete controlled-attachment list. The binary upload, inspection, publication,
download and versioned withdrawal lifecycle is unchanged. A rejected category, unsupported
media type, inactive activity, hidden target, overflow, or stale version fails through the
ordinary reviewed attachment/error path and never changes another group.

### Customer-list sorting

`GET /api/v1/customers` accepts an optional `sortField` plus `sortDirection` pair for the
tenant-scoped, data-range-filtered customer page. The static sort fields are `UPDATED_TIME`,
`CREATED_TIME`, `NAME` and `OWNER_ACTOR`, and the only directions are `ASC` and `DESC`. A client
may instead supply `customSortFieldCode` with `sortDirection` for one active, non-built-in,
scalar-sortable CUSTOMER field from the server-published field catalog. The two field parameters
are mutually exclusive. The server resolves a custom code to its current definition ID before
querying; SQL never receives a caller-provided column or expression. Omission preserves the
existing `UPDATED_TIME DESC, id DESC` order. Supplying either field
other than the default requires the corresponding customer field to be readable and unmasked for
the current member, so sorting cannot become a field-access oracle. Every order adds `id` in the
same direction as a stable pagination tie-breaker.

### Lead-list sorting

`GET /api/v1/leads` accepts the same static `sortField` plus `sortDirection` pair for a
tenant-scoped, data-range-filtered lead page. A client may instead supply
`customSortFieldCode` with `sortDirection` for one active, non-built-in, scalar-sortable LEAD
field from the server-published catalog; it is mutually exclusive with `sortField`. The server
resolves the code to its current definition ID, requires readable and unmasked access, and only
then builds a parameterized correlated-value order. Numeric custom fields use canonical numeric
ordering; the caller's field code never becomes a SQL column or expression. Omission preserves
`UPDATED_TIME DESC, id DESC`, and every explicit order uses the same-direction `id` tie-breaker.

### Contact-list sorting

`GET /api/v1/contacts` accepts the same static `sortField` plus `sortDirection` pair for a
tenant-scoped, data-range-filtered contact page. A client may instead supply
`customSortFieldCode` with `sortDirection` for one active, non-built-in, scalar-sortable CONTACT
field from the server-published catalog; it is mutually exclusive with `sortField`. The server
resolves the code to its current definition ID, requires readable and unmasked access, and only
then builds a parameterized correlated-value order. Numeric custom fields use canonical numeric
ordering; the caller's field code never becomes a SQL column or expression. Omission preserves
`UPDATED_TIME DESC, id DESC`, and every explicit order uses the same-direction `id` tie-breaker.

| Method and path | Authority | Input | Success body |
| --- | --- | --- | --- |
| `POST /api/v1/users` | `users:write` | `UserCreateRequest` JSON | `UserResponse` |
| `GET /api/v1/users/{userId}` | `users:read` | Positive numeric path ID | `UserResponse` |
| `GET /api/v1/users` | `users:read` | Query parameters below | `UserPageResponse` |
| `PUT /api/v1/users/{userId}/status` | `users:write` | `status` query parameter | Empty success body |
| `DELETE /api/v1/users/{userId}` | `users:delete` | Positive numeric path ID | Empty success body |

## Create request

```json
{
  "username": "alice",
  "email": "alice@example.com",
  "phoneNumber": "+12025550123",
  "password": "correct horse battery staple",
  "realName": "Alice Example"
}
```

Passwords are write-only, contain 12–64 Unicode characters, remain within BCrypt's 72-byte UTF-8 input limit, and never appear in responses or logs. Phone numbers use E.164 form, including the leading `+` and country code.

## Query parameters

| Name | Type | Default | Constraint |
| --- | --- | --- | --- |
| `page` | integer | `1` | `1` through `1,000,000` |
| `size` | integer | `20` | `1` through `200` |
| `username` | string | — | At most 50 characters |
| `email` | string | — | Valid email address |
| `status` | string | — | One of the status values below |

`UserPageResponse` contains `pageNum`, `pageSize`, `totalNum`, and an `objectList` of `UserResponse`. `totalNum` is always a non-negative integer. The response has no generic pager or internal metadata carrier, so generated clients can type both the page and its items directly.

## User status

Public status values are `ACTIVE`, `INACTIVE`, and `LOCKED`. `DELETED` represents soft deletion and is changed only through the delete operation. List queries exclude deleted users unless a dedicated administrative use case explicitly requests them.

## Organization operations

Organization state is local to this application: a verified actor has a global member profile and one explicit membership per tenant. Department and review operations require an active membership in the verified current tenant in addition to the listed authority.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `POST /api/v1/organization/bootstrap` | `organization:bootstrap` | Initialize the first profile and membership for the current tenant; it is allowed once per tenant. |
| `PUT /api/v1/organization/profile` | `organization:request` | Create or update the caller's profile data; it cannot change identity or tenant. |
| `GET /api/v1/organization/departments` | `organization:read` | List departments visible in the caller's active tenant. |
| `GET /api/v1/organization/departments/by-ids?ids=…` | `organization:read` | Return up to 200 visible departments by ID; missing or deleted IDs are omitted. |
| `GET /api/v1/organization/departments/{id}/descendants` | `organization:read` | List visible in-tenant descendants, excluding the requested department. |
| `POST /api/v1/organization/departments` | `organization:write` | Create a department under an optional parent in the current tenant. |
| `PUT /api/v1/organization/departments/{id}/name` | `organization:write` | Rename a department. |
| `PUT /api/v1/organization/departments/{id}/parent` | `organization:write` | Move it under an in-tenant parent or the tenant root. |
| `POST /api/v1/organization/departments/{id}/deactivate` | `organization:write` | Deactivate a department with no active children or members. |
| `DELETE /api/v1/organization/departments/{id}` | `organization:write` | Delete a department only when it has no child or member assignment of any status. |
| `GET /api/v1/organization/departments/{id}/members` | `organization:read` | Page active department-workbench rows in a visible department; `keyword` is an optional display-name filter and `includeDescendants=true` asks the server to extend the page scope to active descendants. The row always resolves its department name from membership; `loginName` requires `users:read`, and active `roleNames` require `roles:read`. |
| `GET /api/v1/organization/members` | `organization:read` | Page active local profiles across the caller tenant; `keyword` is an optional display-name filter. |
| `POST /api/v1/organization/membership-requests` | `organization:request` | Submit a request to an initialized target tenant. |
| `POST /api/v1/organization/membership-requests/{id}/withdraw` | `organization:request` | Withdraw only the caller's own pending request. |
| `GET /api/v1/organization/membership-requests` | `organization:write` | Page requests targeting the caller's active tenant. |
| `POST /api/v1/organization/membership-requests/{id}/approve` | `organization:write` | Approve a pending request and activate target-tenant membership. |
| `POST /api/v1/organization/membership-requests/{id}/reject` | `organization:write` | Reject a pending request in the current tenant. |
| `GET /api/v1/organization/tenants` | `organization:request` | List the caller's active memberships as `TenantAccessResponse` entries. |
| `GET /api/v1/organization/tenants/{tenantId}/access` | `organization:request` | Return one `TenantAccessResponse` proving or denying active membership in the target tenant. |

`TenantAccessResponse` contains only `tenantId`, optional `departmentId`, and `granted`; it does not disclose a tenant name, authority set, or credential. Department-member pages use the dedicated `DepartmentMemberProfilePageResponse`, whose `departmentName` is resolved server side; `loginName` and `roleNames` are omitted without their respective authorities, and inactive roles are not projected. `page` and `size` apply to `/organization/members`, `/organization/departments/{id}/members`, and `/organization/membership-requests`, with the limits in the query-parameter table above. A successful local `POST /api/v1/users` also atomically creates an active member profile, current-tenant membership and baseline member role for the persisted user ID; status changes and soft deletion synchronize that member access before the command completes. `targetTenantId` in a membership request and `{tenantId}` in the access route are not tenant-switching mechanisms. They do not change the authenticated caller, create a tenant context, or return a new token. A department parent is resolved only under the caller's current tenant; cross-tenant parents and department cycles are rejected. Structural writes and target-tenant membership submission are serialized per tenant. Deactivation rejects active children or memberships; deletion also rejects inactive child and membership relationships.

## Region reference data

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `GET /api/v1/reference/regions?parentCode=…` | `organization:request` | List deployment-supplied child regions. An omitted `parentCode` lists roots. |

Regions have `code`, `parentCode`, `name`, and `level` fields. This endpoint is independent of organization state and never uses request data to select a tenant. The bundled deployment contains an empty catalog; production deployments provide a curated `@Primary RegionReferenceCatalog` adapter rather than importing an administrative-area data set into organization tables.

## Enterprise data lookup

B23a is a read-only, human-triggered boundary. Every operation requires an active membership in the verified tenant and the tenant-assignable `enterprise-data:read` authority. The request cannot select a tenant, provider account, connector, endpoint, quota plan, or source implementation.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `GET /api/v1/enterprise-data/capabilities` | `enterprise-data:read` | Return safe readiness, query modes, jurisdictions, identifier types, candidate ceiling, and the effective usage mode. |
| `POST /api/v1/enterprise-data/search` | `enterprise-data:read` | Search one fixed page by `registeredName` and explicit `legalJurisdiction`; the name is 1..200 trimmed Unicode code points and the response never includes a provider total or continuation token. |
| `POST /api/v1/enterprise-data/lookup` | `enterprise-data:read` | Look up one exact `(identityScope, registryScheme, value)` selector after scheme-specific syntax and checksum validation. |
| `POST /api/v1/enterprise-data/adoption-preview` | `enterprise-data:read` | Recheck one exact identity for a versioned Customer and return a short-lived, caller-bound selection preview. |
| `POST /api/v1/enterprise-data/adopt` | `enterprise-data:read` | Consume one preview and explicitly selected facts/identities against the supplied Customer version. |

The public OpenAPI contract uses `EnterpriseDataCapabilityResponse` for capabilities,
`EnterpriseDataObservationResponse` for lookup and each search item,
`EnterpriseDataAdoptionPreviewResponse` for the caller-bound preview, and
`EnterpriseDataAdoptionResponse` for adoption confirmation. These named responses expose only
the governed projection; neither the contract nor a response carries a raw provider payload,
provider binding, account data, credential, or arbitrary extension map.

The catalog supports `CN_UNIFIED_SOCIAL_CREDIT_CODE/CN_USCC`, `GLOBAL_LEI_SYSTEM/LEI`, and `UK_COMPANIES_HOUSE/GB_CH_NUMBER`. USCC and LEI are uppercased only according to their own schemes and must pass their check digit; USCC additionally validates the governed registration-department/category pair and the six-digit administrative-division segment. A Companies House number is boundary-trimmed, ASCII-uppercased, exactly eight ASCII letters/digits, and must be either the eight-digit class or start with a prefix in the pinned B23-CH registry-number class catalog; all prefix letters and leading zeros are retained. A generic normalized identifier, unknown two-letter prefix, internal whitespace, punctuation, or Unicode lookalike is not accepted. `legalJurisdiction` remains a separate optional ISO country/subdivision fact and never enters the identity key; the name-search selector still requires an explicit governed jurisdiction. Scheme support does not make a source ready: an adapter may advertise only types backed by its current conformance and deployment policy.

Observations contain only the B23-XP allowlist: primary and additional registered identifiers with standing, optional legal jurisdiction, a registration name capped at the B06 200-code-point limit, coarse `ACTIVE`/`INACTIVE`/`UNKNOWN` entity status, an optional `INCORPORATED`/`REGISTERED`/`FORMED`/`ESTABLISHED`/`OPENED` formation date, one optional registered address, mandatory `sourceDeclaredPartial`, source kind, optional source update/revision, `observedAt`, `expiresAt`, effective usage mode, and context-specific controlled attribution. There is no organization/legal-form type, raw payload, provider attribution text, provider ID, source locator, score, person, contact, ownership, risk, credential, account, or arbitrary extension map. B23a neither saves observations nor writes Customer data.

Adapter capability and output are insufficient by themselves. The core first resolves an independent tenant deployment policy containing the governed source kind, positive TTL, maximum use and `SEARCH_RESULT`/`DETAIL` attribution entries; a missing or disabled policy returns `NOT_READY` without consulting the adapter. It rejects source-kind, TTL, identity, standing or capability mismatches and takes the most restrictive result. Because B23a has no mapping, provenance, retention or adoption transaction, every ready public capability and observation is capped at `EPHEMERAL_ONLY`; missing policy or attribution readiness is `NOT_READY`.

The bundled production behavior is `ready=false`; lookup attempts then return a safe `503`. The `dev` and `test` profiles can use the deterministic synthetic adapter. Stable source outcomes are `404` no result, `429` temporarily limited, `503` unavailable, and `422` unsupported. Provider authentication or authorization failures are source unavailability and never become caller `401`/`403` errors.

## Authorization and navigation

Tenant roles use the code-defined capability catalog. System roles cannot be modified or deleted; custom-role grants and assignments are versioned and bounded by the caller's effective delegation rights.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `GET /api/v1/authorization/me` | `navigation:read` | Return the current member's effective tenant authorities, data scopes, and verified authentication-context authorities used by server navigation. |
| `GET /api/v1/authorization/capabilities` | `authorization:read` | List tenant-assignable capability definitions. |
| `GET /api/v1/authorization/roles` | `authorization:read` | List tenant roles; `keyword` is a literal substring of the role name, including `%` and `_`. |
| `POST /api/v1/authorization/roles` | `authorization:write` + `authorization:assign` | Create a custom role with delegable grants. |
| `PUT /api/v1/authorization/roles/{roleId}` | `authorization:write` + `authorization:assign` | Update a custom role by version. |
| `DELETE /api/v1/authorization/roles/{roleId}?version=…` | `authorization:write` + `authorization:assign` | Delete an unused custom role by version. |
| `POST /api/v1/authorization/roles/{roleId}/copy` | `authorization:write` + `authorization:assign` | Copy a delegable role. |
| `PUT /api/v1/authorization/roles/{roleId}/grants` | `authorization:write` + `authorization:assign` | Atomically replace delegable grants. |
| `GET /api/v1/authorization/roles/{roleId}/assignments` | `authorization:read` | List assigned active members. |
| `POST /api/v1/authorization/roles/{roleId}/assignments` | `authorization:assign` | Assign the role to a bounded actor list. |
| `DELETE /api/v1/authorization/roles/{roleId}/assignments/{actorId}` | `authorization:assign` | Remove one assignment without accepting a tenant from the request. |
| `GET /api/v1/authorization/members/{actorId}/roles` | `authorization:read` | List a visible tenant member's roles. |
| `POST /api/v1/authorization/members/roles/query` | `authorization:read` | Batch-query role assignments for a bounded actor list. |
| `GET /api/v1/navigation` | `navigation:read` | Return navigation filtered by effective capability and current preference. |
| `GET /api/v1/navigation/catalog` | `authorization:read` | Return the full code-defined catalog for administration. |
| `GET /api/v1/navigation/preferences` | `navigation:read` | Read the current member's preference baseline and current-authority menu candidates before the preference is applied. |
| `PUT /api/v1/navigation/preferences` | `navigation:write` | Replace the current member's bounded preference by version. |

Navigation preferences affect presentation only. They never add authorities, and an item without an effective capability remains absent even if its key is present in a preference. The preference response includes `availableItems`, a current-caller authority-filtered tree without the member's own preference applied; clients must use it rather than the administration catalog to offer hidden-menu recovery. The `authorities` returned by `/authorization/me` contain the same effective tenant grants plus verified authentication-context authorities used by navigation, so a client-side route guard cannot reject an entry that the server made reachable solely through a trusted platform permission. The tenant-assignable `workflows:read` capability yields the `workflow.definitions` entry (`/workflows`); `reports:read` yields `reports` (`/reports`); and `oa-forms:read`, `oa-requests:read`, and `oa-announcements:read` independently yield the corresponding `/oa/forms`, `/oa/requests`, and `/oa/announcements` entries. Management authorities remain independently assignable and are returned by `/authorization/me` for UI guards.

## Customers

Customer capabilities are tenant assignable and data scoped. `customers:read`, `customers:export`, `customers:write`, `customers:delete`, and `customers:assign` resolve independently, so a broad read grant does not widen an export, write, delete, or assignment operation. `SELF` includes records owned by or explicitly shared with the current member for ordinary reads; export uses its independent owner-derived predicate and never adds collaboration. `DEPARTMENT` and `DEPARTMENT_SUBTREE` expand by the active owner's department; `TENANT` includes all active customers in the verified tenant. A collaborator with `READ` is excluded from write queries, export owner scope, delete, and assignment.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `GET /api/v1/customers` | `customers:read` | Page visible active non-pool customers. Optional non-blank `keyword` searches the currently eligible name, phone, telephone, and email fields; `name` and `ownerActorId` remain direct field-protected filters. `starred` and current-member `savedViewId` are also supported. With `activities:read`, each `CustomerResponse` may include the newest ACTIVE non-system activity whose direct subject is that returned customer as `lastActivityTime` and `lastActivityContent`; the summary uses no related-record activity and both fields are omitted otherwise. |
| `POST /api/v1/customers` | `customers:write` | Create one customer; assigning another owner or collaborators additionally requires `customers:assign`. |
| `GET /api/v1/customers/tags` | `customers:read` | List up to 200 tags derived only from visible customers; NFKC/case-equivalent display names share one catalog entry, and the tags field must be unmasked readable. |
| `POST /api/v1/customers/duplicate-check` | `customers:read` | Return a boolean for visible name, phone, or custom unique-field duplicates without exposing a matching record. |
| `POST /api/v1/customers/export/csv` | `customers:export` + `customers:read` | Synchronously download a fully generated bounded CSV for the intersection of export owner scope and ordinary read visibility. The body is UTF-8 BOM CSV with fixed `customer_id` plus 1–50 requested active/readable/unmasked scalar field codes. |
| `GET /api/v1/customers/nearby` | `customers:read` | Page visible customers whose primary geocoded address is within `radiusMeters` (1 to 50000); `addresses` must be unmasked readable. |
| `GET /api/v1/customers/summaries?ids=…` | `customers:read` | Return visible field-filtered summaries for up to 100 IDs. |
| `POST /api/v1/customers/batch/tags` | `customers:write` | Add/remove tags for up to 100 customer/version pairs atomically; removal uses the same NFKC/case-insensitive key as validation. |
| `GET /api/v1/customers/{customerId}` | `customers:read` | Return one visible customer with field access and masking applied. |
| `GET /api/v1/customers/{customerId}/business-overview` | `customers:read` | Return seven fixed, stable-order business cards for an exact visible customer. Each source independently requires its own read authority, data scope, and unmasked required fields; an unavailable source keeps its own `source`, sets `status: UNAVAILABLE`, and omits `count` and `amount`. |
| `PUT /api/v1/customers/{customerId}` | `customers:write` | Version-update supplied details, addresses, and custom values. |
| `DELETE /api/v1/customers/{customerId}?version=…` | `customers:delete` | Soft-delete a customer without active children. |
| `PUT /api/v1/customers/{customerId}/state` | `customers:write` | Version-update lock and `OPEN`/`WON` deal state. |
| `PUT /api/v1/customers/{customerId}/owner` | `customers:assign` | Version-update the owner within the grant's assignment scope. |
| `PUT /api/v1/customers/{customerId}/tags` | `customers:write` | Version-replace the bounded tag collection. |
| `GET /api/v1/customers/{customerId}/collaborators` | `customers:assign` | List collaborators for an owner-scope visible customer. |
| `PUT /api/v1/customers/{customerId}/collaborators` | `customers:assign` | Version-replace active-tenant collaborators and their `READ`/`WRITE` access. |
| `DELETE /api/v1/customers/{customerId}/collaborators/me?version=…` | `customers:read` | Remove the current member's own collaboration entry. |
| `PUT /api/v1/customers/{customerId}/parent` | `customers:write` | Version-set or clear a visible parent while rejecting hierarchy cycles. |
| `GET /api/v1/customers/{customerId}/hierarchy` | `customers:read` | Return the bounded visible portion of the customer's ancestor/descendant hierarchy; hidden descendants do not count toward the visible result limit, and parent writes keep every complete tree within 64 levels/2,000 nodes. |
| `PUT /api/v1/customers/{customerId}/star` | `customers:read` | Idempotently set the current member's personal star without changing customer version. |

Every create and update first applies record authority, then field policy. The tenant owner dynamically has unmasked `WRITE`; other roles need explicit field entries. Omitted update properties retain their current value, an empty `phoneNumber` clears the optional phone number, an empty address list clears addresses, and an empty value list removes an optional custom field. Hidden or read-only fields cannot be supplied. Custom values are keyed by immutable server-generated field code and validated for type, required state, select options, active tenant member/department references, and database-enforced uniqueness. URL length is checked again after conversion to its ASCII persistence representation. `FILE` and custom `RELATION` values remain unavailable until their owning modules exist.

The business-overview response is `{ customerId, cards }`. The public card order is `CONTACT`, `OPEN_OPPORTUNITY`, `ACTIVE_CONTRACT`, `PENDING_INVOICE`, `ACTIVE_RECEIVABLE`, `ACTIVE_RECEIVABLE_PLAN`, then `RETURN_VISIT`. It never returns linked-record identities or names. A hidden, cross-tenant, pooled, or deleted customer fails before source queries; `AVAILABLE` cards use their documented direct-customer lifecycle predicates and return a count plus an amount only where applicable. An unavailable card is exactly `{ source, status: "UNAVAILABLE" }`: it does not use `source: "UNAVAILABLE"` and omits `count` and `amount` rather than serializing them as `null`.

Customer CSV export accepts only ordered `fieldCodes`, optional `keyword`, `name`, `ownerActorId`, `starred`, and the caller-owned `savedViewId`; it does not accept pagination, sort, tenant, separator, encoding, locale, timezone, filename, SQL, or object-type parameters. It excludes pools/deleted records and all relation, collection, address, member, attachment, FILE, registered-identity, and provenance data. Supported selected types are `TEXT`, `TEXTAREA`, `SELECT`, `DATE`, `DATETIME`, `INTEGER`, `DECIMAL`, `BOOLEAN`, `EMAIL`, `PHONE`, and `URL`; an unknown, inactive, hidden, masked, duplicate, or unsupported field rejects the entire request. The result has `Content-Type: text/csv;charset=UTF-8`, `Content-Disposition: attachment; filename="customers.csv"`, `Cache-Control: no-store`, and `X-Content-Type-Options: nosniff`. It uses CRLF rows, RFC 4180 quoting, stable code headers, locale-independent scalar values, formula-prefix hardening, `customer_id ASC`, a 5,001-row rejection probe, and a 16 MiB pre-body limit. It creates no artifact, attachment, job, history, receipt, or download token.

Built-in customer field codes are `customer_name`, `phone_number`, `telephone`, `email`, `website`, `description`, `tags`, `addresses`, `owner_actor`, `parent_customer`, `locked`, `deal_status`, `status`, `created_time`, and `updated_time`. Saved views and direct value predicates may use searchable built-in or custom fields only when the caller has unmasked read access. A customer response always includes identity and version, but protected field values are omitted or deterministically masked; `dealTime`, `createdTime`, and `updatedTime` are null whenever their governing field is hidden or masked. Mutation responses include `parentCustomerId` only when that parent is visible under the command's record scope, including a parent just validated by create or parent update. The shared `collaborators` field is populated by assignment-authorized create, owner, and collaborator commands; ordinary read/write responses keep it empty and callers use the dedicated assignment endpoint for current values. Out-of-scope, cross-tenant, deleted, and pool records all return not found through ordinary customer routes.

The customer, contact, lead, opportunity, product, return-visit, contract, invoice, receivable, and receivable-plan list contracts accept an optional `keyword` of at most 200 trimmed Unicode code points; blank input means no keyword filter. Search candidates are fixed by the object's server-side catalog, then intersected with the current active, searchable, readable, and completely unmasked field policy. Customer/contact/lead candidates are name, phone, telephone, and email; opportunity uses name; product uses name plus its NFKC/lowercase code key; return visit uses its generated number; contract uses name plus its NFKC/lowercase number key; invoice uses application number, title, and invoice number; receivable uses receipt number; receivable plan uses an exact positive installment. Text uses an escaped literal prefix, so `%` and `_` are ordinary characters. An invalid exact-installment keyword or no catalog candidate surviving current policy returns no rows. Every keyword predicate remains inside the existing verified-tenant, record-scope, active/non-deleted boundary; ordinary customer/contact search also excludes public-pool rows.

## Customer pools

Customer-pool capabilities are tenant assignable but deliberately separate from customer record capabilities. Pool configuration uses `customer-pools:manage`; accessible reads, release, claim, assignment, and pool-customer deletion use their own capability codes. Ordinary members must also be a pool administrator, an explicitly eligible active member, or an active member of an eligible department as required by the operation. Tenant ownership is resolved from persisted tenant roles, never from a request flag. A tenant can retain at most 500 non-deleted pools; creation locks the initialized tenant row before checking this limit.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `GET /api/v1/customer-pools` | `customer-pools:manage` | Page non-deleted pool configuration. |
| `POST /api/v1/customer-pools` | `customer-pools:manage` | Create an active versioned pool with bounded administrator, eligibility, limit, cooldown, and rule configuration, subject to the 500-pool tenant limit. |
| `GET /api/v1/customer-pools/catalog` | `customer-pools:read` | List every active pool accessible to the current member without exposing management configuration; tenant owners can see every active pool. |
| `GET /api/v1/customer-pools/{poolId}` | `customer-pools:manage` | Get tenant-scoped pool configuration. |
| `PUT /api/v1/customer-pools/{poolId}` | `customer-pools:manage` | Version-update a pool as its administrator or tenant owner. |
| `PUT /api/v1/customer-pools/{poolId}/status` | `customer-pools:manage` | Activate or deactivate an empty pool by version. |
| `DELETE /api/v1/customer-pools/{poolId}?version=…` | `customer-pools:manage` | Soft-delete an empty pool by version. |
| `POST /api/v1/customer-pools/{poolId}/transfer` | `customer-pools:manage` | Move every membership to a different active pool under ordered row locks. |
| `GET /api/v1/customer-pools/{poolId}/customers` | `customer-pools:read` | Page `IN_POOL` customers in an accessible pool; optional `name` and current-member `savedViewId` filters follow customer field policy. |
| `GET /api/v1/customer-pools/{poolId}/customers/{customerId}` | `customer-pools:read` | Return one field-filtered customer from an accessible pool. |
| `POST /api/v1/customer-pools/{poolId}/customers/release` | `customer-pools:release` + `customers:write` | Atomically move up to 100 versioned, write-visible active customers into the pool. |
| `POST /api/v1/customer-pools/{poolId}/customers/claim` | `customer-pools:claim` | Claim up to 100 versioned pool customers as the current member, subject to eligibility, daily limit, and previous-owner cooldown. |
| `POST /api/v1/customer-pools/{poolId}/customers/assign` | `customer-pools:assign` | As a pool administrator, assign up to 100 versioned pool customers to one active tenant member. |
| `DELETE /api/v1/customer-pools/{poolId}/customers` | `customer-pools:delete` | As a pool administrator, soft-delete up to 100 versioned pool customers without active children. |

Pool entry sets `status=IN_POOL`, clears the owner and collaborators, and creates one tenant-scoped membership row; the database key prevents membership in two pools. Claim or assignment removes that row, restores `status=ACTIVE`, sets an active in-tenant owner, and records immutable history. Claim counts exclude administrative assignments. MySQL locks the pool and membership while checking limits, so Redis is not part of correctness. Pool customer output and saved-view predicates reuse the CUSTOMER field catalog: hidden fields are omitted, masked fields stay masked, and masked fields cannot be used as direct or saved-view predicates. `parentCustomerId` is returned only when the parent remains in the same accessible pool and the relationship field is readable.

The generated contract names `CustomerPoolPageResponse` for configuration pages and `CustomerPoolCustomerPageResponse` for pool-customer pages. Both provide `pageNum`, `pageSize`, `totalNum`, and a typed `objectList`; catalog, detail, and mutation responses likewise name `CustomerPoolResponse` or `CustomerResponse`, so clients do not infer shapes from generic pager objects.

## Leads

Lead capabilities are independent: `leads:read`, `leads:export`, `leads:write`, `leads:delete`, `leads:assign`, and `leads:convert` resolve their own `SELF`, department, or tenant predicate. Leads use their own field catalog, tags, collaborators, hierarchy, stars, saved views, and optimistic version. Conversion requires the independent customer and contact write boundaries as well as `leads:convert`; every copied source field, including the owner, must be unmasked readable and every target field must be writable. It then persists immutable `customerId` and `convertedContactId` associations before changing the lead to `CONVERTED`. Converted leads remain visible to normal read and saved-view queries but all mutations and a second conversion are rejected.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `GET`, `POST /api/v1/leads`; `GET /api/v1/leads/{leadId}` | `leads:read`, `leads:write` | Query, create, and read tenant-scoped active or converted leads; query `converted=true/false` narrows only the caller-visible set to converted or active records. Optional `keyword` uses the shared field-aware candidate rules, while `name` remains a direct literal-prefix filter. `sortField` and `sortDirection` accept only the documented server-owned ordering fields and direction. With `activities:read`, each listed `LeadResponse` may include `lastActivityTime` and `lastActivityContent` from the newest ACTIVE non-system activity whose direct subject is that returned lead; both fields are omitted otherwise. |
| `POST /api/v1/leads/export/csv` | `leads:export` + `leads:read` | Synchronously download a fully generated bounded CSV for the intersection of owner-derived export scope and ordinary Lead read visibility. |
| `PUT /api/v1/leads/batch/owner` | `leads:assign` | Atomically transfer 1–100 distinct visible leads to one active member within the resolved assignment scope. Every target carries its observed version; owner field policy, target visibility, membership, and scope are rechecked before any lead changes. |
| `POST /api/v1/leads/batch/delete` | `leads:delete` | Atomically soft-delete 1–100 distinct visible leads with observed versions. The server validates every target before withdrawing an attachment or file field, so a stale, hidden, or invalid target deletes none of the selection. |
| `POST /api/v1/leads/{leadId}/convert` | `leads:convert` | Atomically create the customer/contact projections and finalize the lead. |
| `PUT`, `DELETE /api/v1/leads/{leadId}` and bounded relation routes | Respective `leads:*` authority | Versioned mutable lead operations; converted leads are immutable. |
| `GET`, `POST /api/v1/lead-intake-clients`; `DELETE /api/v1/lead-intake-clients/{clientId}?version=…` | `leads:intake-manage` | List credential-free typed client summaries, create, or version-deactivate an active client used for public intake. Creation accepts `{}` and returns `{ client, apiKey }`; the `apiKey` is returned only once, while list and deactivation responses return only the `client`-shaped summary. Its current owner must also have `leads:write`, a usable lead data scope, and write access to `lead_name` and `owner_actor`. |
| `POST /api/v1/open/leads` | Active API key | Submit the bounded public lead payload using `X-API-Key: <tenantLocator>.<keyId>.<secret>` and receive only `{ "accepted": true }`. |

The public payload cannot choose a tenant, owner, authority, or custom field. The credential's non-secret tenant locator only scopes the client lookup; it must use the canonical unpadded 11-character Base64URL form, while `keyId` is case-sensitive. The secret must still verify an active client whose owner membership remains active before its tenant and owner are derived. The intake security context receives only `leads:write`; application data-scope and field-policy checks still apply. Client creation rejects an owner without the public submission baseline (`leads:write`, a usable lead data scope, and write access to `lead_name` and `owner_actor`). Every submission still executes under that owner's current business and field policy, so revoking a baseline permission or sending an optional field without current write access rejects the submission. The server generates a 256-bit secret and returns the complete `apiKey` (`tenantLocator.keyId.secret`) only in the create response; clients must store it securely because neither subsequent responses nor logs contain it. A tenant can have at most 100 active intake clients. The local default rate limits are 20 failed authentication attempts per source per minute and 60 submissions per verified key per minute. Authentication atomically holds a source in-flight slot before lookup, releases it after a verified credential, counts only rejected credentials, and cancels it after an authentication-infrastructure failure. An unknown credential fingerprint is held in a separate bounded pending cache and reaches tenant verified-key capacity only after secret verification. A key ID is attached to its reservation before lookup, so deactivating a client invalidates both settled and in-flight local verified-key buckets; tenant partitions preserve live quotas and fail closed when their bounded capacity is full. Only explicitly configured direct proxy addresses may supply a client address through `X-Forwarded-For`. Otherwise the direct peer is the source, so reverse-proxy deployments should configure trusted proxies or apply this limit at the gateway.

Lead list sorting is a fixed server contract: `sortField` accepts `UPDATED_TIME`, `CREATED_TIME`, `NAME`, or `OWNER_ACTOR`, and `sortDirection` accepts `ASC` or `DESC`. Omitting both retains `UPDATED_TIME DESC, id DESC`; each non-default order follows with `id` in the same direction for stable pagination. Physical database columns are mapped from the server enum, never request text, and a non-default field requires the caller to retain unmasked read access to its matching lead field.

Lead CSV export accepts only ordered `fieldCodes`, optional `keyword`, `name`, `ownerActorId`, `subordinateOwnerOnly`, `starred`, `converted`, the caller-owned `savedViewId`, and temporary `conditions`; unknown JSON properties are rejected, and a saved view cannot be combined with temporary conditions. It accepts 1–50 current active/readable/unmasked scalar field codes of types `TEXT`, `TEXTAREA`, `SELECT`, `DATE`, `DATETIME`, `INTEGER`, `DECIMAL`, `BOOLEAN`, `EMAIL`, `PHONE`, or `URL`. Tags, addresses, owner, customer, parent-Lead, FILE, attachments, relation values, unknown, inactive, hidden, masked, duplicate, or unsupported fields reject the whole request. Customer and parent-Lead conditions may filter but never project cells, accept only `EQ`/`IN`, and recheck current target visibility. The completed result has `Content-Type: text/csv;charset=UTF-8`, `Content-Disposition: attachment; filename="leads.csv"`, `Cache-Control: no-store`, and `X-Content-Type-Options: nosniff`; it is UTF-8 BOM RFC 4180 CSV with CRLF, stable code headers, formula-prefix hardening, `lead_id ASC`, a 5,001-row rejection probe, and a 16 MiB pre-body limit. It creates no artifact, attachment, job, history, receipt, or download token.

## Opportunities

Opportunity capabilities are independent: `opportunities:read`, `opportunities:write`, `opportunities:delete`, and `opportunities:assign` each resolve their own `SELF`, department, subtree, or tenant predicate. Collaborator `READ`/`WRITE` access widens only the corresponding read/write predicate. A different owner must be an active tenant member inside the assignment grant's scope. The required customer and optional contacts are accepted and returned only through the caller's independent current customer/contact read visibility; every supplied contact must belong to the selected customer and the primary contact must also belong to the related contact set. The same invariant is checked on create, full customer replacement, contact replacement, and primary-contact changes; a tenant/opportunity/contact/customer composite relation key also prevents a concurrent or bypass write from separating the persisted facts.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `POST /api/v1/opportunities/query` | `opportunities:read` | Return the typed `OpportunityPageResponse` page of visible active opportunities by field-aware name `keyword`, direct literal name prefix, owner, customer, outcome, personal star, current-member typed saved view, and documented server-owned sort field/direction. Its `objectList` contains `OpportunityResponse` items. With `activities:read`, each item may include `lastActivityTime` from the newest ACTIVE non-system activity whose direct subject is that returned opportunity; the field is omitted otherwise. |
| `PUT /api/v1/opportunities/batch/owner` | `opportunities:assign` | Atomically transfer 1–100 distinct visible opportunities to one active member within the resolved assignment scope. Every target carries its observed version; owner field policy, target visibility, membership, and scope are rechecked before any opportunity changes. |
| `POST /api/v1/opportunities/batch/delete` | `opportunities:delete` | Atomically soft-delete 1–100 distinct visible opportunities with observed versions. The server validates all targets before withdrawing an attachment or file field, so a stale, hidden, or invalid target deletes none of the selection. |
| `POST /api/v1/opportunities/summary` | `opportunities:read` | Return visible count and estimated-amount total for the same bounded query after requiring unmasked amount access. |
| `POST /api/v1/opportunities` | `opportunities:write` | Create a versioned opportunity; a different owner or collaborators additionally require `opportunities:assign`. |
| `GET /api/v1/opportunities/{opportunityId}` | `opportunities:read` | Return one visible field-filtered opportunity. |
| `GET /api/v1/opportunities/{opportunityId}/editable-baseline` | `opportunities:write` | Return the authoritative replacement baseline; business values appear only when every replaced field is readable, unmasked and writable. |
| `PUT /api/v1/opportunities/{opportunityId}` | `opportunities:write` | Version-replace ordinary details, customer, and custom values while the opportunity is open. This replaces the basic fields rather than applying a partial update, so a browser must build it only from a current `editable-baseline` response. |
| `DELETE /api/v1/opportunities/{opportunityId}?version=…` | `opportunities:delete` | Soft-delete an owner-scope visible active opportunity. |
| `PUT /api/v1/opportunities/{opportunityId}/owner`, `/collaborators` | `opportunities:assign` | Version-change the scoped active owner or replace the active-tenant collaboration set. |
| `DELETE /api/v1/opportunities/{opportunityId}/collaborators/me?version=…` | `opportunities:read` | Let a visible non-owner collaborator remove only themselves without assignment authority. |
| `PUT /api/v1/opportunities/{opportunityId}/contacts`, `/primary-contact` | `opportunities:write` | Version-replace visible contacts or set/clear the primary member of that set; every selected contact must belong to the opportunity customer. |
| `GET /api/v1/opportunities/{opportunityId}/contacts/baseline` | `opportunities:read + opportunities:write + contacts:read` | Return the complete versioned association baseline only when customer/contact relation fields are unmasked and writable, and every related contact is currently visible. Otherwise return only `id`, `version`, and `editable=false`; no hidden relation is exposed. |
| `POST /api/v1/opportunities/{opportunityId}/contacts/query` | `opportunities:read` + `contacts:read` | Page currently visible related contacts with contact-name masking; opportunity contact fields must also be unmasked readable. |
| `PUT /api/v1/opportunities/{opportunityId}/stage` | `opportunities:write` | Version-change the pipeline and stage codes while open; an actual change requires an active exact catalog pair. |
| `PUT /api/v1/opportunities/{opportunityId}/outcome` | `opportunities:write` | Change to `WON`, `LOST`, or `INVALID`, or explicitly reopen with `OPEN`. |
| `PUT /api/v1/opportunities/{opportunityId}/star` | `opportunities:read` | Idempotently set the caller's personal star without changing aggregate version. |
| `GET /api/v1/opportunities/{opportunityId}/products` | `opportunities:read` + `products:read` | Read a typed `OpportunityProductsResponse` complete immutable product snapshot only when the five snapshot source fields remain unmasked readable. |
| `PUT /api/v1/opportunities/{opportunityId}/products` | `opportunities:write` + `products:read` | Version-replace at most 100 distinct currently available product lines and return the same typed snapshot after requiring `products` field write access and unmasked snapshot-source reads. |

Create requires `name`, `customerId`, `pipelineCode`, and `stageCode`; owner defaults to the verified caller. Contact and collaborator collections are each bounded to 100 entries, and custom values to 200 fields. Every ordinary mutation supplies the current version. Changing an opportunity customer revalidates all current contacts against the next customer; a contact cannot move to another customer or be detached while such a relationship remains. Changing or clearing a primary contact requires the `contacts` collection to be unmasked readable before membership is tested, so hidden relationships cannot be probed. Opportunity dates are limited to `1000-01-01` through `9999-12-31`; datetime inputs, saved-view values, and server-generated outcome times are truncated to microseconds before persistence or comparison. A non-open outcome records server time and an optional remark; every outcome transition requires write access to both `outcome` and `outcome_remark`, including a null remark that clears the prior value. Reopening clears both closing fields. Closed opportunities remain readable and summarizable but reject ordinary detail, stage, owner, collaborator, contact, and custom-value mutation until reopened. Pipeline and stage codes are managed by the implemented B32a tenant catalog: creation and an actual pair change require an active exact pair, while an exact unchanged pair may remain on an otherwise editable Opportunity after deactivation or deletion. B11 workflow stages do not own or synchronize these codes; see the [B32 plan](../batch-B32.md) and [acceptance record](acceptance/B32a.md).

Opportunity list sorting accepts the fixed `sortField` values `UPDATED_TIME`, `CREATED_TIME`, `NAME`, or `OWNER_ACTOR` with `sortDirection` `ASC` or `DESC`. A client may instead send `customSortFieldCode` with `sortDirection` for one active, non-built-in, scalar-sortable OPPORTUNITY field from the server-published catalog; the two field parameters are mutually exclusive. The server resolves the custom code to its current definition ID, requires readable and unmasked access, and only then builds a parameterized correlated-value order. Numeric custom fields use canonical numeric ordering; the caller's field code never becomes a SQL column or expression. Omitting both retains `UPDATED_TIME DESC, id DESC`, and every explicit order follows with `id` in the same direction for stable pagination.

Built-in opportunity field codes are `opportunity_name`, `customer`, `primary_contact`, `contacts`, `pipeline_code`, `stage_code`, `expected_close_date`, `estimated_amount`, `next_contact_time`, `description`, `owner_actor`, `outcome`, `outcome_time`, `outcome_remark`, `status`, `products`, `created_time`, and `updated_time`. Direct and saved-view predicates require unmasked readable fields. Before SQL translation, every saved-view value is validated and canonicalized by its current field definition; invalid numbers, dates, booleans, identifiers, choices, and other typed values are rejected. Only `IN` removes duplicate normalized values; `BETWEEN` preserves both ordered boundaries, including equal point-range bounds. `CONTAINS` and `NOT_CONTAINS` validate bounded text fragments for email, phone, and URL fields, while equality operators still require complete typed values. Customer and primary-contact saved-view relations accept only `EQ`, `IN`, `IS_EMPTY`, and `IS_NOT_EMPTY`; target-bearing operators validate every referenced record before querying. Hidden values are omitted, masked text is deterministic, and masked identifiers, dates, times, and amounts are null. Contracts, approval/workflow configuration, files, import/export, activity feeds, and cross-domain panels are outside this boundary.

## Opportunity pipelines

`opportunity-pipelines:read` and `opportunity-pipelines:manage` are tenant-catalog capabilities: they require an active tenant member and deliberately do not resolve record data scopes. Pipeline and stage codes are immutable, binary-exact ASCII identifiers (`[A-Za-z0-9][A-Za-z0-9._-]{0,63}`); names, display order, and stage probability are mutable. New records begin `INACTIVE`; a pipeline needs an active stage before activation, an active pipeline cannot lose its final active stage, and terminal deletion requires an inactive pipeline with only deleted stages or an inactive stage respectively. The non-deleted capacity is 100 pipelines per tenant and 100 stages per pipeline.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `POST /api/v1/opportunity-pipelines/query`, `GET /api/v1/opportunity-pipelines/{pipelineId}` | `opportunity-pipelines:read` | Page or read the tenant pipeline catalog, including inactive or deleted history when requested. |
| `POST /api/v1/opportunity-pipelines` | `opportunity-pipelines:manage` | Create an inactive pipeline with an immutable code. |
| `PUT /api/v1/opportunity-pipelines/{pipelineId}`, `/status` | `opportunity-pipelines:manage` | Version-update display facts or activate/deactivate the pipeline. |
| `DELETE /api/v1/opportunity-pipelines/{pipelineId}?version=…` | `opportunity-pipelines:manage` | Terminally soft-delete an inactive pipeline only after all of its stages are deleted. |
| `POST /api/v1/opportunity-pipelines/{pipelineId}/stages/query`, `GET /api/v1/opportunity-pipelines/{pipelineId}/stages/{stageId}` | `opportunity-pipelines:read` | Page or read stages under one exact pipeline. |
| `POST /api/v1/opportunity-pipelines/{pipelineId}/stages` | `opportunity-pipelines:manage` | Create an inactive stage with immutable code and probability. |
| `PUT /api/v1/opportunity-pipelines/{pipelineId}/stages/{stageId}`, `/status` | `opportunity-pipelines:manage` | Version-update display facts/probability or activate/deactivate the stage. |
| `DELETE /api/v1/opportunity-pipelines/{pipelineId}/stages/{stageId}?version=…` | `opportunity-pipelines:manage` | Terminally soft-delete an inactive stage while its parent pipeline is not deleted. |

V85 stores codes with `ascii_bin`, backfills every existing exact Opportunity pair without rewriting it, and enforces the pair through a same-tenant foreign key. A fresh tenant gets active `direct/qualification` and `direct/negotiation` defaults. Codes remain returned only through the existing B05 Opportunity fields; stage probability never changes Opportunity outcome, summary, forecast, saved-view, report, or B11 workflow behavior.

## Products and categories

Product capabilities are tenant-catalog capabilities: `products:read`, `products:write`, `products:delete`, `products:assign`, and `products:export`. Each requires an active tenant membership, but none accepts or resolves a record data scope. Product ownership remains explicit responsibility metadata and does not hide catalog rows.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `POST /api/v1/products/query` | `products:read` | Page active products by field-aware name/normalized-code `keyword`, direct literal name/code, category, owner, availability, current-member typed PRODUCT saved view, and documented server-owned sort field/direction. |
| `POST /api/v1/products/export/csv` | `products:read` + `products:export` | Generate one bounded CSV from the current field-policy-safe catalog projection and supported list filters. |
| `POST /api/v1/products` | `products:write` | Create a versioned product; required fields need `WRITE`, while optional fields need it only when supplied. Choosing a different active owner additionally requires `products:assign`. |
| `GET /api/v1/products/{productId}` | `products:read` | Return one active product after current PRODUCT field filtering and masking. |
| `GET /api/v1/products/{productId}/editable-baseline` | `products:read` + `products:write` | Return `id`, `version`, and `editable`; business values are included only when every complete-replacement field is readable, unmasked, and writable. |
| `PUT /api/v1/products/{productId}` | `products:write` | Version-replace the detail, optional category, and custom-field values. |
| `PUT /api/v1/products/{productId}/availability` | `products:write` | Version-change between `AVAILABLE` and `UNAVAILABLE` with field write access. |
| `PUT /api/v1/products/{productId}/owner` | `products:assign` | Version-assign an active tenant member with owner-field write access. |
| `DELETE /api/v1/products/{productId}?version=…` | `products:delete` | Soft-delete the product, force it unavailable, and withdraw its ordinary attachments and structured FILE bindings. |
| `GET /api/v1/product-categories` | `products:read` | Return the complete active tenant category tree. |
| `GET /api/v1/product-categories/{categoryId}` | `products:read` | Return one active category. |
| `POST /api/v1/product-categories` | `products:write` | Create a root or child category. |
| `PUT /api/v1/product-categories/{categoryId}` | `products:write` | Version-rename or move a category after cycle and depth checks. |
| `DELETE /api/v1/product-categories/{categoryId}?version=…` | `products:delete` | Soft-delete only when no active child or non-deleted product depends on it. |

Product `name`, `code`, and non-negative two-decimal `listPrice` are required; `unit` and `description` are optional, owner defaults to the verified caller, and availability defaults to `AVAILABLE`. The tenant code key is the NFKC/lowercase form and is database-unique among non-deleted products. `PUT` replaces its six basic fields rather than applying a partial update, so a browser must build it only from a current `editable-baseline` response with `editable=true`; the `false` response intentionally contains no business values, while `categoryId`, `unit`, and `description` retain their real nullable values when it is true. Custom fields are bounded to 200 codes and 20 values per field; current definitions normalize typed values before persistence, while unique values are finally arbitrated by MySQL. Built-in PRODUCT field codes are `product_name`, `product_code`, `category`, `unit`, `list_price`, `description`, `owner_actor`, `availability`, `status`, `created_time`, and `updated_time`. Query predicates require the governing field to be unmasked readable. Create accepts optional `fileFieldDraftId` and create/update accept `fileFields: { fieldCode: [fileId...] }`; each FILE field has at most ten ordered files and responses expose only readable, unmasked values. Ordinary attachment routes use `/api/v1/attachment-targets/product/{productId}/attachments` and require the corresponding `files:read/write` plus `products:read/write`.

Product CSV export accepts 1–50 ordered `fieldCodes`, optional `keyword`, literal name/code, category, owner, availability, static or active non-built-in scalar custom sorting, and either a caller-owned PRODUCT `savedViewId` or temporary `conditions`; unknown JSON properties, duplicate fields, a saved view combined with temporary conditions, invalid category/view/filter/sort data, relation fields (`category`, `owner_actor`), FILE values, inactive/hidden/masked/unsupported fields, and more than 5,000 matching rows reject the whole request. It preserves the validated requested sort inside one read-only `REPEATABLE_READ` snapshot, writes UTF-8 BOM RFC 4180 CRLF rows with stable `product_id` plus field-code headers, locale-independent scalar values and spreadsheet-formula hardening, and rejects output above 16 MiB before returning the body. The result has `Content-Type: text/csv;charset=UTF-8`, `Content-Disposition: attachment; filename="products.csv"`, `Cache-Control: no-store`, and `X-Content-Type-Options: nosniff`; it creates no artifact, attachment, job, history, receipt, or download token.

### Product image groups

Product media is a relation-level `PRIMARY` or `DETAIL` image group, not a filename convention or
custom FILE value. Existing products use the same controlled attachment route with an optional
`productImageType=PRIMARY|DETAIL` list parameter and the corresponding field in an upload-intent
request. Only Product accepts this field; it accepts PNG/JPEG only and each group permits at most
nine active uploading or published files under the Product root lock. Group lists retain the
server's stable available-time/file-ID order; omitting the parameter retains the ordinary complete
attachment list.

Before a Product exists, `POST /api/v1/product-image-drafts` creates a member-owned private draft.
`POST /{draftId}/images/{imageType}/upload-intents`, `PUT .../files/{fileId}/content?version=…`,
and `GET .../upload-status` provide the normal controlled byte/inspection lifecycle, while
`DELETE /{draftId}?version=…` withdraws the exact staged set. All draft routes require both
`products:write` and `files:write`. `ProductCreateRequest` can carry the draft ID plus the exact
`productImages` map; the product transaction publishes and binds only that clean, owner-matched
set, consumes the draft, and rejects any missing, extra, duplicate, expired, rejected, or stale
candidate. A draft never creates a targetless downloadable file or a public storage URL.

Product list sorting accepts the fixed `sortField` values `UPDATED_TIME`, `CREATED_TIME`, `NAME`, or `OWNER_ACTOR` with `sortDirection` `ASC` or `DESC`. A client may instead send `customSortFieldCode` with `sortDirection` for one active, non-built-in, scalar-sortable PRODUCT field from the server-published catalog; the two field parameters are mutually exclusive. The server resolves the custom code to its current definition ID, requires readable and unmasked access, and only then builds a parameterized correlated-value order. Numeric custom fields use canonical numeric ordering; the caller's field code never becomes a SQL column or expression. Omitting both retains `UPDATED_TIME DESC, id DESC`, and every explicit order follows with `id` in the same direction for stable pagination.

Category names are NFKC/lowercase-unique within the same parent. A tenant can retain at most 5,000 active categories and a tree can be at most 16 levels deep. The server serializes category reads and writes through a tenant catalog guard before checking cycles, moves, dependencies, and normalized uniqueness. Cross-tenant category or product IDs fail closed.

An opportunity product replacement accepts the current opportunity `version` and an ordered `lines` list. Every line has a distinct positive `productId`, a positive quantity with at most four fractional digits, and an optional non-negative two-decimal `unitPrice`; omitted price uses the product list price. `product_name`, `product_code`, `category`, `unit`, and `list_price` must all be unmasked readable before those values can be exposed or frozen. The catalog guard is acquired before the command transaction's first consistent read, so a concurrent product move and its category name cannot be mixed across transaction snapshots. The response returns server-calculated `discountRate`, `lineAmount`, total, and the new opportunity version. A catalog edit, withdrawal, or soft deletion does not change an existing snapshot.

## Contracts and invoices

Contract capabilities are `contracts:read`, `contracts:export`, `contracts:write`, `contracts:delete`, and `contracts:assign`; invoice capabilities are `invoices:read`, `invoices:write`, `invoices:delete`, `invoices:assign`, and `invoices:issue`. Each capability resolves its own record data scope. A contract collaborator can widen contract read or write visibility only through their explicit collaborator access while its optional inclusive `effectiveUntil` date has not passed; owner assignment, deletion, and selected CSV export remain owner-scope operations. List keywords are field-policy aware: contracts use the eligible name and normalized number key, while invoices use the eligible application number, title, and invoice number. Hidden, masked, inactive, or non-searchable candidates never participate.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `POST /api/v1/contracts/query` | `contracts:read` | Page visible active or voided contracts by field-aware name/normalized-number `keyword`, customer, opportunity, status, and documented server-owned sort field/direction. |
| `POST /api/v1/contracts/summary` | `contracts:read` + `receivables:read` | Return count, total contract amount, receipt amount and outstanding amount after the exact same validated contract filters; contract and receipt data scopes plus the two amount field policies are enforced independently. |
| `POST /api/v1/contracts/batch/export/csv` | `contracts:read` + `contracts:export` | Produce one controlled, non-cacheable CSV for 1–100 distinct current-list `{contractId, version}` rows only after read and export scope intersection plus current-version rechecks. Output is limited to fixed readable/unmasked contract columns, protects formula-like text, omits relationships/custom fields, and is byte-bounded. |
| `GET /api/v1/contracts/{contractId}` | `contracts:read` | Return one visible field-filtered `ContractResponse` and only currently readable relationships and product snapshots. |
| `POST /api/v1/contracts` | `contracts:write` | Create and return a field-filtered, versioned `ContractResponse`; another owner or collaborators additionally require `contracts:assign`, the owner must fit that scope, and only non-empty product lines require `products:read`. |
| `PUT` / `PATCH /api/v1/contracts/{contractId}` | `contracts:write` | Version-update details, relationships, product snapshots, whole-order discount, and custom values, then return the field-filtered `ContractResponse`. |
| `GET /api/v1/contracts/{contractId}/products` | `contracts:read` + `products:read` | Return a complete versioned `ContractProductsResponse` only when the contract `products` field and all five product snapshot source fields are readable, unmasked values. The dedicated payload includes product identity, display snapshot, quantity, and unit price but deliberately excludes derived discounts and contract totals. |
| `PUT /api/v1/contracts/{contractId}/products` | `contracts:write` + `products:read` | Atomically replace all 0–100 contract product lines from a complete versioned snapshot. The server locks the contract and product catalog, revalidates availability and field policy, recalculates all derived amounts, and rejects a new total below committed invoices, receipts, or active receivable plans. |
| `PUT /api/v1/contracts/{contractId}/owner`, `/collaborators` | `contracts:assign` | Version-change the owner within the resolved assignment scope or replace the active-tenant collaboration set. Collaborators carry `READ`/`WRITE` plus nullable, inclusive `effectiveUntil`; null means unlimited. |
| `PUT /api/v1/contracts/batch/owner` | `contracts:assign` | Atomically transfer 1–100 distinct visible contracts to one active member within the resolved assignment scope. Each target carries its observed version; the owner field policy, target visibility, membership, and scope are rechecked before any contract is changed. |
| `PUT` / `DELETE /api/v1/contracts/batch/collaborators` | `contracts:assign` | Atomically add/update named active collaborators, including one nullable inclusive `effectiveUntil` per collaborator, or remove named non-owner collaborators, across 1–100 distinct visible observed-version contracts. Add retains every collaborator not named by the request; remove is idempotent for a record that does not contain the named collaborator. Versions, owner scope, target visibility, collaborator uniqueness, owner exclusion, non-past expiry, and active-member status are preflighted before any save. |
| `DELETE /api/v1/contracts/{contractId}/collaborators/me?version=...` | `contracts:write` | Let a visible non-owner collaborator remove only themselves. |
| `PUT /api/v1/contracts/{contractId}/void` | `contracts:write` | Version-transition an active contract to `VOIDED`. |
| `DELETE /api/v1/contracts/{contractId}?version=...` | `contracts:delete` | Soft-delete a contract only while no non-deleted invoice references it. |
| `POST /api/v1/contracts/batch/delete` | `contracts:delete` | Atomically soft-delete 1–100 distinct visible contracts with observed versions. The server preflights all invoice, receipt, and receivable-plan dependencies before withdrawing any attachments or deleting a contract. |
| `POST /api/v1/invoices/query` | `invoices:read` | Page visible invoice applications by field-aware application-number/title/invoice-number `keyword`, customer, contract, or status. |
| `GET /api/v1/invoices/{invoiceId}` | `invoices:read` | Return one visible field-filtered invoice application. |
| `POST /api/v1/invoices` | `invoices:write` + `contracts:read` | Create a `PENDING` invoice against a visible active contract; another owner additionally requires `invoices:assign` and must fit its scope. |
| `PUT /api/v1/invoices/{invoiceId}` | `invoices:write` + `contracts:read` | Version-update a pending application's amount, profile fields, remark, and custom values. |
| `PUT /api/v1/invoices/{invoiceId}/owner` | `invoices:assign` | Version-assign an active tenant member within the resolved assignment scope. |
| `PUT /api/v1/invoices/batch/owner` | `invoices:assign` | Atomically transfer 1–100 distinct visible invoices to one active member within the resolved assignment scope. Every target carries its observed version; owner field policy, membership and scope are rechecked before any invoice changes. |
| `PUT /api/v1/invoices/{invoiceId}/issue`, `/reset` | `invoices:issue` | Register the server-side issue result or reset it to `PENDING`. |
| `PUT /api/v1/invoices/batch/reset` | `invoices:issue` | Atomically reset 1–100 distinct visible `ISSUED` invoices with observed versions. Field policy and every state/version are preflighted before any invoice resets. |
| `DELETE /api/v1/invoices/{invoiceId}?version=...` | `invoices:delete` | Soft-delete an invoice application. |
| `POST /api/v1/invoices/batch/delete` | `invoices:delete` | Atomically soft-delete 1–100 distinct visible `PENDING` invoices with observed versions. Any version, visibility or state failure prevents every delete and attachment withdrawal. |
| `GET /api/v1/invoice-profiles?customerId=...` | `invoices:read` + `customers:read` | List at most 100 active profiles for one currently visible customer. |
| `POST /api/v1/invoice-profiles`, `PUT /api/v1/invoice-profiles/{profileId}` | `invoices:write` + `customers:read` | Create within the 100-active-profile customer limit or version-update a profile without changing its customer. |
| `DELETE /api/v1/invoice-profiles/{profileId}?version=...` | `invoices:delete` + `customers:read` | Soft-delete a profile after rechecking its customer's visibility. |

A contract requires a visible customer; optional opportunity and contact relationships must remain independently visible and belong to that customer. Composite foreign keys prevent either related record from later moving to another customer while referenced. Contract lines accept only distinct product IDs, positive quantities, and optional sale prices. At most 100 lines are snapshotted, and catalog name, code, category, unit, list price, discount, subtotal, and total are server-derived. Empty line collections do not require product capability or PRODUCT field reads. Contract numbers use tenant-local NFKC/lowercase keys and are rejected if the normalized key exceeds 100 characters. Every line and the aggregate subtotal/total must fit `DECIMAL(22,2)`.

Contract list sorting is either a fixed server enum or one resolved custom field. The static `sortField` accepts `UPDATED_TIME`, `CREATED_TIME`, `NAME`, or `OWNER_ACTOR`, and `sortDirection` accepts `ASC` or `DESC`. Alternatively, `customSortFieldCode` selects one current active, non-built-in scalar CONTRACT definition and requires `sortDirection`; it cannot be combined with `sortField`. Both modes retain the default `UPDATED_TIME DESC, id DESC` when absent and follow a non-default order with `id` in the same direction for stable pagination. The application rechecks readable, unmasked field access and resolves the custom code to a definition ID before persistence; physical columns, IDs, scalar expressions, and numeric casts are server-owned and never request text.

Invoice list sorting follows the same policy-checked static-or-custom pattern. Static `sortField` accepts only `UPDATED_TIME`, `CREATED_TIME`, `REQUESTED_DATE`, `ISSUED_DATE`, `APPLICATION_NUMBER`, `INVOICE_NUMBER`, or `OWNER_ACTOR`; alternatively `customSortFieldCode` selects one active, non-built-in scalar INVOICE definition with `sortDirection`. The modes are mutually exclusive, absent sorting keeps `UPDATED_TIME DESC, id DESC`, and a non-default order adds same-direction `id` for stable pagination. The server resolves the custom field only after current readable, unmasked policy checks and owns its correlated value expression and numeric casts. Amount, tax, payment, profile, and other sensitive or mutable built-in invoice fields are deliberately not sortable.

Invoice customer identity is derived from the locked contract and is also enforced by the `(tenant, contract, customer)` foreign key. Invoice creation and amount changes, as well as contract customer or total changes, serialize on the contract row. The sum of all non-deleted `PENDING` and `ISSUED` invoices cannot exceed the contract total; a contract with invoices cannot change customer or be deleted. Invoice profiles use `ACTIVE`/`DELETED`; `INDIVIDUAL` profiles clear company tax, bank, address, and phone values, while `ORGANIZATION` profiles require a tax number. Each customer can retain at most 100 active profiles; creation is serialized on the tenant invoice guard and list queries use the same hard bound. Built-in field codes are defined by the CONTRACT and INVOICE catalogs and every read/write applies the current B05 field policy.

## Receivables and receivable plans

Receivable capabilities are `receivables:read`, `receivables:write`, `receivables:delete`, and `receivables:assign`; plan capabilities are `receivable-plans:read`, `receivable-plans:write`, and `receivable-plans:delete`. Each capability resolves an independent record data scope. Routes are `POST /api/v1/receivables/query`, `POST /api/v1/receivables/summary`, `GET/POST /api/v1/receivables`, `PUT/DELETE /api/v1/receivables/{id}`, `PUT /api/v1/receivables/{id}/owner`, `PUT /api/v1/receivables/batch/owner`, and `POST /api/v1/receivables/batch/delete`; the two batch routes atomically process 1–100 distinct visible `receivableId`/observed-`version` pairs and never partially transfer or delete. Plans provide `POST /api/v1/receivable-plans/query`, `POST /api/v1/receivable-plans/summary`, detail, create, update, delete, and `POST /batch` routes, plus `GET /api/v1/receivable-plans/available?contractId={id}`. Receivable query `keyword` searches only the currently eligible receipt number by literal prefix; plan query `keyword` searches only the eligible positive installment by exact integer value, including leading-zero input. Payment methods do not participate. Receivable pages accept only the paired `sortField`/`sortDirection` values `RECEIVED_DATE`, `CREATED_TIME`, `UPDATED_TIME`, `RECEIPT_NUMBER`, or `OWNER_ACTOR` with `ASC`/`DESC`; absent values preserve received-date descending order, and non-default fields additionally require unmasked readable field access.

Plan queries and plan summaries accept one owner scene: `ownerActorId` selects one field-authorized owner, while `subordinateOwnerOnly=true` asks the server to resolve the verified caller's current department subtree within the already-resolved plan data scope. They are mutually exclusive. An empty resolved subtree returns an empty result; the browser cannot submit member IDs or an alternate hierarchy, and the exact same predicate controls the list and every plan amount total.

`ReceivablePlanQueryRequest` also accepts a positive `savedViewId` or at most 50 `conditions`, but never both. A saved view is read only from the verified member's own RECEIVABLE_PLAN view set. Each temporary or persisted condition is checked against the caller's current active, searchable, readable and unmasked field catalog; values are normalized by field type, and CUSTOMER/CONTRACT equality values must be separately visible through their own read scope. The repository binds only resolved builtin columns or fixed custom-field metadata and reuses the resulting predicate for pagination and the plan summary.

Receipt and plan customer identity is always derived from the visible contract. The database repeats the relation with tenant/contract/customer composite foreign keys. Receipt create/update locks the active contract before computing all active receipts, so the amount cannot exceed the contract total or the selected active plan's planned amount. Plan creation, batch creation, and amount update use the same contract lock and reject a total planned amount above the contract. Plan installment numbers are positive and unique among active plans of a contract. `customSortFieldCode` provides the alternative to `sortField`: it can name only an active, non-built-in scalar RECEIVABLE definition that is currently readable and unmasked; it must not be combined with `sortField`, and `sortDirection` remains required. The server resolves the trusted definition ID before persistence, and INTEGER/DECIMAL values are ordered numerically. List keywords use only the current field-policy-eligible receipt number or exact positive installment; payment methods are not candidates, and a non-integer plan keyword returns an empty page. A plan with any receipt history cannot change or be deleted; `receivedAmount`, `remainingAmount`, and `progress` are derived from active receipts, and a fully settled plan is not returned by the available-plan route. Contract total reduction below active receipts and contract deletion while receipts or plans exist both fail closed. All amount fields are bounded `DECIMAL(22,2)` values, and every endpoint applies current RECEIVABLE or RECEIVABLE_PLAN field policy. RECEIVABLE approval facts are read from the persisted receipt and current customer, contract, and plan relationship visibility is rechecked before projection.

`POST /api/v1/receivables/query` now publishes a named `ReceivablePageResponse` whose items are `ReceivableResponse`; `POST /api/v1/receivables/summary` publishes `ReceivableListSummaryResponse`. The summary takes the exact same query request and returns count plus total amount after the validated list predicate; it requires `receivables:read` and unmasked RECEIVABLE `amount` access. `POST /api/v1/receivable-plans/summary` publishes `ReceivablePlanListSummaryResponse`; it reuses the exact validated plan query, requires unmasked RECEIVABLE_PLAN `planned_amount`, and independently requires `receivables:read` plus unmasked RECEIVABLE `amount` before its correlated actual-receipt aggregate applies the caller's separate receipt scope. Receipt get/create/update/owner responses use that same named item schema. `ReceivableResponse.approvalStatus` is the latest closed approval-instance state for only receipt records already admitted by current receivable visibility; it is null when no instance exists and omits workflow people, tasks, and facts.

## Activities and return visits

Activity routes require `activities:read`, `activities:write`, or `activities:delete`; these are membership capabilities rather than a second data-scope model. The subject record remains authoritative: creation/update/delete requires the owning business aggregate's current write scope, while query/get requires its current read scope. The supported subject and relation types are `CUSTOMER`, `CONTACT`, `LEAD`, `OPPORTUNITY`, `CONTRACT`, `INVOICE`, `RECEIVABLE`, and `RECEIVABLE_PLAN`. A relation is not returned as an expanded record and cannot grant its own visibility. Only active `FOLLOW_UP` entries authored by the caller can change; a system entry has no public write route. The versioned update request replaces `nextContactTime`; it is nullable in the OpenAPI contract, so an explicit `null` clears the stored next-contact reminder.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `POST /api/v1/activities/query` | `activities:read` + subject read scope | Page the timeline for one visible subject. |
| `GET`, `PUT /api/v1/activities/options` | `activities:read`, `activities:write` | Read or atomically replace the tenant's governed follow-up methods and templates. |
| `GET /api/v1/activities/{activityId}` | `activities:read` + subject read scope | Get a visible timeline entry. |
| `POST /api/v1/activities`, `/batch` | `activities:write` + subject write scope | Create one or up to 100 bounded follow-ups atomically. |
| `PUT /api/v1/activities/{activityId}` | `activities:write` + subject write scope | Version-update the caller's own active follow-up. |
| `DELETE /api/v1/activities/{activityId}?version=…` | `activities:delete` + subject write scope | Soft-delete the caller's own active follow-up. |

Return-visit routes use independent `return-visits:read`, `:write`, `:delete`, and `:assign` authorities, each with its own data scope and `RETURN_VISIT` field policy. A create always supplies a customer visible through `customers:read`; optional contact and contract IDs must remain visible through their own read scopes and must belong to the same customer. `visitForm` is a closed optional `IN_PERSON|PHONE|SMS|EMAIL|WECHAT` value and `satisfaction` is a closed optional `VERY_SATISFIED|SATISFIED|NEUTRAL|DISSATISFIED|VERY_DISSATISFIED` value; both are separately field-policy checked, persisted on the root and may be omitted by historical clients. An update retains an omitted form or satisfaction and clears either only when the request explicitly sends `null`. Output suppresses any relationship that is no longer independently visible. The server creates the visit number and only stores it with the versioned aggregate.

Return-visit list sorting is policy checked. `sortField` accepts only `VISIT_TIME`, `UPDATED_TIME`, `CREATED_TIME`, `VISIT_NUMBER`, or `OWNER_ACTOR`; `sortDirection` is `ASC` or `DESC`. Alternatively, `customSortFieldCode` may name one active, non-built-in scalar RETURN_VISIT definition that the caller can read without masking. The static and custom inputs are mutually exclusive, a custom sort also requires direction, and the server resolves its trusted definition ID before persistence; INTEGER and DECIMAL values sort numerically. Leaving all sorting inputs absent retains `VISIT_TIME DESC, id DESC`, while every non-default order follows with `id` in the same direction for stable pagination. Physical columns and custom-value expressions are owned by the server, never request text.

`POST /api/v1/return-visits/query` publishes a named `ReturnVisitPageResponse` whose items are `ReturnVisitResponse`; return-visit get/create/update/owner responses use the same item schema. `ReturnVisitResponse.approvalStatus` has the same visibility-scoped, nullable latest-instance meaning as receivables and never includes task, actor, route, or approval-fact data.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `POST /api/v1/return-visits/query`, `GET /{id}` | `return-visits:read` | Page current-scope active return visits, including optional field-aware visit-number `keyword`, or get one visit. |
| `GET /api/v1/return-visits/options?keyword=…&limit=…` | `return-visits:read` | Return at most 100 visible number/time options; default limit is 20. |
| `POST /api/v1/return-visits`, `PUT /{id}` | `return-visits:write` | Create or version-update a customer-bound visit, typed custom values, and structured FILE bindings. |
| `PUT /api/v1/return-visits/{id}/owner` | `return-visits:assign` | Version-assign an active member in the assignment scope. |
| `DELETE /api/v1/return-visits/{id}?version=…` | `return-visits:delete` | Soft-delete a visit and withdraw its ordinary attachments and structured FILE bindings. |

ReturnVisit create accepts optional `fileFieldDraftId` and create/update accept `fileFields: { fieldCode: [fileId...] }`; each FILE field has at most ten ordered files. Output and download require `files:read`, current return-visit read scope, and an unmasked FILE field READ; ordinary attachment routes use `/api/v1/attachment-targets/return-visit/{returnVisitId}/attachments` and add `files:read/write` to the matching current `return-visits:read/write` scope.

`GET /api/v1/worklist?date=YYYY-MM-DD&size=50&cursor=…` requires `worklist:read`. `size` defaults to 50 and is limited to 100. The response is `{ "items": [...], "nextCursor": "..." }`; `nextCursor` is null only when every enabled source is exhausted. The opaque, date-bound `v3` cursor stores one stable `(dueTime, sourceId)` position per source, so global sorting and page truncation never advance a source past an item that was not consumed. A saved `v2` cursor remains readable: its eight positions are retained, the absent `WORK_TASK` position is set to exhausted for that continuation, and any following cursor is emitted as `v3`. A cursor-free query starts all nine sources.

The worklist merges customer follow-ups from activity `nextContactTime`, contact/lead/opportunity next-contact fields, return visits, receivable-plan reminder dates, contract expiry dates, pending approval tasks assigned to the caller, and `WORK_TASK` reminders. Every CRM/OA source is rechecked through its current capability, owning record data scope, and required unmasked fields. `WORK_TASK` additionally requires `work-tasks:read` and is selected by a dedicated tenant/current-assignee/active-member SQL projection over `TODO` or `IN_PROGRESS` tasks due on the requested day; creator, follower, manager-only, terminal, deleted, undated, and differently assigned tasks are excluded before the source limit. Its subject type and ID are `WORK_TASK` and the task ID; an optional CRM relationship is neither returned nor used for access. `POST /api/v1/worklist/acknowledgements` accepts a date plus either up to 100 exact item fingerprints or one `kind`. Confirmations are tenant/member scoped and do not mutate source records; a category request fails closed if its bounded verification scan cannot reach the end of the current worklist. A Work-task fingerprint is `(WORK_TASK, taskId, worklistProjectionVersion, dueTime)`, so relevant projection changes reappear while comments, followers, CRM links, and description-only edits remain acknowledged.

## Work tasks

B21 Work tasks are independent manually managed collaboration facts; they do not reuse approval tasks, stage tasks, activities, or worklist acknowledgements. All routes require an active membership. `work-tasks:manage` never implies read or write: tenant-wide reads require both `work-tasks:read` and `work-tasks:manage`, while manager mutations require `work-tasks:write` and `work-tasks:manage`.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `POST /api/v1/work/tasks/query` | `work-tasks:read` | Page tasks visible through creator/assignee/follower participation, optionally filtered by one currently readable CRM record, or all tenant tasks when the caller also has `work-tasks:manage`. |
| `GET /api/v1/work/tasks/{taskId}` | `work-tasks:read` | Get one currently visible non-deleted task. |
| `POST /api/v1/work/tasks` | `work-tasks:write` | Create a `TODO` task assigned to one active member of the verified tenant. |
| `PUT /api/v1/work/tasks/{taskId}` | `work-tasks:write`; creator or manager | Optimistic-version replace title, optional description, priority, and optional due time while open. |
| `PUT /api/v1/work/tasks/{taskId}/assignee` | `work-tasks:write`; creator or manager | Reassign an open task to one active member in the same tenant. |
| `PUT /api/v1/work/tasks/{taskId}/followers` | `work-tasks:write`; creator or manager | Optimistic-version replace up to 50 unique active tenant followers on an open task. |
| `PUT /api/v1/work/tasks/{taskId}/business-record` | `work-tasks:write`; visible participant or manager; owning CRM write scope for a non-null replacement | Replace or clear the one optional CRM record on an open task. |
| `GET /api/v1/work/tasks/{taskId}/comments?page=…&size=…` | `work-tasks:read`; visible participant or manager | Page active comments in creation/ID order. |
| `POST /api/v1/work/tasks/{taskId}/comments` | `work-tasks:write`; visible participant or manager | Append non-empty plain text of at most 5,000 Unicode code points. |
| `DELETE /api/v1/work/tasks/{taskId}/comments/{commentId}?version=…` | `work-tasks:write`; active author | Optimistic-version soft-delete the author's active comment. |
| `POST /api/v1/work/tasks/{taskId}/start` | `work-tasks:write`; current assignee | Move `TODO` to `IN_PROGRESS` and record the immutable first-start time. |
| `POST /api/v1/work/tasks/{taskId}/return-to-todo` | `work-tasks:write`; current assignee | Move `IN_PROGRESS` back to `TODO` without clearing first-start time. |
| `POST /api/v1/work/tasks/{taskId}/complete` | `work-tasks:write`; current assignee | Move an open task to terminal `DONE`. |
| `POST /api/v1/work/tasks/{taskId}/cancel` | `work-tasks:write`; creator or manager | Move an open task to terminal `CANCELLED`. |
| `DELETE /api/v1/work/tasks/{taskId}?version=…` | `work-tasks:write`; creator or manager | Soft-delete only a never-started, uncommented `TODO` task. |

Create input contains `title`, optional `description`, optional `priority` (default `NORMAL`), optional UTC `dueTime`, required `assigneeActorId`, optional `followerActorIds`, and an optional paired `businessObjectType`/`businessRecordId`. The CRM type whitelist is `CUSTOMER`, `CONTACT`, `LEAD`, `OPPORTUNITY`, `CONTRACT`, `INVOICE`, `RECEIVABLE`, and `RECEIVABLE_PLAN`. Creation and a non-null replacement check the explicit new record under its owning domain's current write authority and data scope and require its tenant to match the Work tenant. Clearing or replacing does not resolve the previous relationship, so an unreadable old target and no old target have the same authorization behavior. Title is trimmed non-empty plain text of at most 200 Unicode code points; description is nullable plain text of at most 10,000 code points. Priorities are `LOW`, `NORMAL`, `HIGH`, and `URGENT`. Structural, follower, relationship, and state mutations use optimistic versions. Tenant, creator, manager identity, status, first-start/completion/cancellation time, audit time, and projection version are always server facts and are not request properties.

Query input extends the standard `page`/`size` contract with optional `participantRole` (`ALL`, `CREATOR`, `ASSIGNEE`, or `FOLLOWER`), up to four `statuses`, up to four `priorities`, optional inclusive `dueFromInclusive`, optional exclusive `dueBefore`, and an optional paired CRM relationship filter. Relationship filtering first requires the target record to be readable inside the query's `REPEATABLE_READ` transaction, then applies the task relationship and participation predicates in SQL before `totalNum` and page slicing from that same snapshot. `DELETED` is never queryable. Results are fixed to `updatedTime DESC, id DESC`; client sort fields, SQL, expressions, and column names are not accepted. Cross-tenant, invisible, inactive-participant, and deleted IDs all return the same not-found response.

Responses include creator, assignee, current follower IDs, structure, optional CRM relationship, priority, due time, computed `overdue`, lifecycle facts, optimistic `version`, and audit times. Relationship creation and each non-null replacement require owning-domain write access to the explicit new target; clearing and replacement do not inspect an unreadable previous target. A relationship-filter query checks owning-domain read access inside the same read snapshot as count and page slicing. Every response batches linked IDs by CRM type and reapplies current owning-domain read scope; an unreadable/deleted relationship is omitted without hiding an otherwise visible task. CRM readability never makes a nonparticipant task visible, and task participation never grants CRM access. `DONE` and `CANCELLED` cannot be reopened. The worklist projection version changes only with title, priority, assignee, due time, or status; descriptions, followers, comments, and CRM relationship changes do not advance it. A soft-deleted comment remains historical and prevents draft-task deletion. B21c consumes the projection version through the dedicated `WORK_TASK` source without creating an Activity or mutating task state during acknowledgement.

B27b adds fixed reminder plans and a Work-only in-app inbox. A plan has at most eight `BEFORE_DUE` (1 minute–30 days), `AT_DUE` (zero offset), or `OVERDUE` (1 minute–30 days) steps, each addressed to a non-empty combination of `ASSIGNEE`, `CREATOR`, and `FOLLOWERS`. An active task needs a due time for a non-empty plan; clearing the due time clears its plan. A non-empty task or series plan is rejected with a domain-rule response when any derived reminder due time or retry deadline falls outside MySQL `DATETIME(6)` (`1000-01-01` through `9999-12-31 23:59:59.999999`). Series plans are templates copied only into subsequently generated tasks. Title, priority, assignee, followers, due time, task state, and plan changes advance a separate notification projection and retire obsolete pending occurrences; description, comments, CRM relationship, and attachments do not. An overdue step creates `WORK_TASK_ESCALATION` only and never mutates the task or worklist.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `GET`, `PUT /api/v1/work/tasks/{taskId}/reminders` | `work-tasks:read`; `work-tasks:write` plus creator or manager for PUT | Get or optimistic-version replace a visible task's reminder plan. |
| `GET`, `PUT /api/v1/work/task-series/{seriesId}/reminders` | `work-tasks:read`; `work-tasks:write` plus creator or manager for PUT | Get or replace a future series template's reminder plan. |
| `GET /api/v1/notifications?page=…&size=…&unreadOnly=…` | `notifications:read` and `work-tasks:read` | Page the caller's currently visible, non-withdrawn Work reminder notifications. |
| `POST /api/v1/notifications/{notificationId}/read` | `notifications:read` and `work-tasks:read` | Idempotently store the first database-UTC read time for a currently visible notification. |
| `GET /api/v1/notifications/preferences` | `notifications:read` | Return only the caller's typed EMAIL opt-in state. |
| `PUT /api/v1/notifications/preferences` | `notifications:read` | Replace only the caller's `emailEnabled` value; every unknown field, including endpoint, provider, account, tenant and recipient selectors, is rejected by contract. |

Notification rows contain only notification kind, task ID, title snapshot, priority snapshot, due time, status, and timestamps. SQL applies current active membership and current task creator/assignee/follower participation before count, page slicing, or a read write; losing access therefore hides old snapshots rather than disclosing a task ID or title. B27c adds only a self-only EMAIL preference: missing preference is OFF, while inbox visibility/read remains independent. There is still no public notification send/retry API, arbitrary recipient/address, webhook, provider receipt, or public delivery ledger.

Work recurrence is a separate future template resource; it never changes a generated task after creation. A series accepts only `DAILY`, `WEEKLY`, or `MONTHLY` rules with an IANA `zoneId`, fixed local time, start/end local dates, and a bounded interval. Weekly rules use one or more ISO weekdays; monthly rules use one day or `lastDayOfMonth`. Public status uses `DRAFT`, `ACTIVE`, `PAUSED`, `STOPPED`, and `ENDED`; `STOPPED` intentionally hides the internal deletion representation. `GET` list/detail use named `WorkTaskSeriesListItemResponse`/`WorkTaskSeriesDetailResponse`, while all mutations return `WorkTaskSeriesMutationResultResponse` with the new version, status and server-authorized actions.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `GET /api/v1/work/task-series`, `GET /api/v1/work/task-series/{id}` | `work-tasks:read` | Page or get a visible non-deleted series with caller-specific `allowedActions`; the browser must not infer edit/activation rights from creator identity or authority. |
| `POST /api/v1/work/task-series` | `work-tasks:write` | Create a DRAFT template with active same-tenant participants. |
| `PUT /api/v1/work/task-series/{id}` | `work-tasks:write`; creator or manager | Versioned update of future generation only. DRAFT/PAUSED templates remain editable when the execution lane is not ready. |
| `POST /api/v1/work/task-series/{id}/activate`, `/pause` | `work-tasks:write`; creator or manager | Versioned future scheduling state change. Activation requires the enabled, reconciled recurrence lane and a future occurrence; it fails rather than silently ending the template. |
| `DELETE /api/v1/work/task-series/{id}?version=…` | `work-tasks:write`; creator or manager | Stop future occurrence generation without deleting historical facts. |
| `GET /api/v1/work/task-series/{id}/occurrences` | `work-tasks:read`; visible participant or manager | Page immutable MATERIALIZED/SKIPPED occurrence facts. |

Each generated task is an ordinary Work task with immutable `RECURRENCE` origin evidence: series/generation/sequence, nominal local time, zone, resolved offset, and UTC due time. Downtime coalesces to only the newest due occurrence. A series with a generated `TODO` or `IN_PROGRESS` task records `SKIPPED/OPEN_TASK` rather than accumulating work. Current creator/assignee loss of Work read/write pauses the series; no caller identity is synthesized by the worker. The primary series projection does not disclose creator identity or internal safe reasons; occurrence responses expose `safeReason`. It is a fixed uppercase code, currently `PAUSED`, `DELETED`, `TIME_RANGE`, `OCCURRENCE_CAP`, `COALESCED_MISFIRE`, `OPEN_TASK`, `CREATOR_ACCESS_REVOKED`, and `ASSIGNEE_ACCESS_REVOKED`; `safeReason` is non-null for `SKIPPED` occurrences.

## Connector security and control plane

B22a control-plane routes are human APIs. Every request requires an active membership; reads require `connectors:read`, while creation, key lifecycle, and status lifecycle require `connectors:manage`. No request accepts a tenant, connector reference, key ID for creation, or secret. Connector references and credentials are generated by the server and remain bound to the caller's verified tenant.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `POST /api/v1/connectors/query` | `connectors:read` | Page connector summaries by optional `ACTIVE`, `DISABLED`, or `RETIRED` status. |
| `GET /api/v1/connectors/{connectorId}` | `connectors:read` | Read one tenant-scoped connector and its key metadata. |
| `POST /api/v1/connectors` | `connectors:manage` | Create an active connector from an empty `{}` body and return its first complete credential once. |
| `POST /api/v1/connectors/{connectorId}/credentials` | `connectors:manage` | Add a rotation key by optimistic `version` and return that complete credential once; at most two keys may be active. |
| `DELETE /api/v1/connectors/{connectorId}/credentials/{keyId}?version=…` | `connectors:manage` | Revoke one currently active key by optimistic version. |
| `PUT /api/v1/connectors/{connectorId}/status` | `connectors:manage` | Change status by optimistic version; `DISABLED` is recoverable and `RETIRED` is irreversible. |

`ConnectorResponse` contains `id`, globally opaque `connectorRef`, status, key metadata (`keyId`, `status`, creation/revocation times), root version and audit times; it never includes a credential. `POST /query` returns `ConnectorPageResponse` with the normal `pageNum`, `pageSize`, `totalNum`, and credential-free `objectList`. Only successful create and rotation return `ConnectorCredentialCreatedResponse`, whose `credential` has the exact `connectorRef.keyId.secret` form together with a safe `connector` summary. Retain that value in a secret store because later reads cannot recover it. Retirement revokes every key. A disabled or retired connector cannot authenticate new ingress, and a retired connector cannot be re-enabled, rotated, or otherwise changed.

`POST /api/v1/connector-ingress/probe` is the only B22a non-human route. It accepts exactly one `Authorization: Bearer connectorRef.keyId.secret`, accepts no body, and requires the resulting authority-free connector principal plus authenticator-issued in-process evidence. Human authentication, direct context construction, and development headers cannot call it, while a connector bearer cannot call human APIs. Invalid or inactive credentials return `401`, exhausted local admission returns `429`, and credential or identity authentication-store failure returns `503`. Rotation keys of one connector share the configured connector request quota. A successful probe only proves that the credential and current registration are active; it does not accept a call fact, create an inbox row, or grant CRM data access.

## Reports

The fixed B17a commercial overview is read-only and does not introduce a shared reporting data scope. Its route first requires an active membership and `reports:read`; it then independently resolves `opportunities:read`, `contracts:read`, and `receivables:read` with each capability's current data scope. It requires unmasked read access to `estimated_amount` and `outcome` for opportunity buckets, `total_amount` and `status` for active contracts, and `amount` for receipts. A missing source capability or hidden/masked report field rejects the complete response rather than leaking a partial aggregate. These field checks and all five aggregations execute in one read-only `REPEATABLE_READ` transaction.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `GET /api/v1/reports/core` | `reports:read` + `opportunities:read` + `contracts:read` + `receivables:read` | Return visible OPEN/WON/LOST opportunity counts and estimated amounts, active-contract count/amount, and active-receipt count/amount. |
| `POST /api/v1/reports/trends` | `reports:read` + selected source `*:read` | Return one fixed metric grouped by DAY, WEEK, or MONTH, including zero-filled periods. |
| `POST /api/v1/reports/rankings` | `reports:read` + selected source `*:read` | Return one fixed metric grouped and stably ranked by owner actor. |
| `GET /api/v1/reports/catalog?objectType=...` | `reports:read` + selected source `*:read` | Return the caller's currently executable time, dimension, SUM, and filter field catalog. |
| `POST /api/v1/reports/definitions/query` | `reports:read` | Page the current member's own active definition summaries. |
| `POST /api/v1/reports/definitions` | `reports:write` + selected source `*:read` | Create one personal single-source definition. |
| `GET /api/v1/reports/definitions/{definitionId}` | `reports:read` + selected source `*:read` | Read an owned definition after current-field revalidation. |
| `PUT /api/v1/reports/definitions/{definitionId}` | `reports:write` + selected source `*:read` | Optimistic-version replace an owned definition; the source is immutable. |
| `DELETE /api/v1/reports/definitions/{definitionId}?version=...` | `reports:write` | Optimistic-version soft-delete an owned definition without requiring surviving source access. |
| `POST /api/v1/reports/definitions/{definitionId}/run` | `reports:read` + selected source `*:read` | Execute an owned definition under current source scope and field policy. |

The response fields are `openOpportunityCount`, `openOpportunityEstimatedAmount`, `wonOpportunityCount`, `wonOpportunityEstimatedAmount`, `lostOpportunityCount`, `lostOpportunityEstimatedAmount`, `activeContractCount`, `activeContractAmount`, `receivableCount`, and `receivableAmount`. Voided and deleted contracts, deleted receipts, and invalid opportunities are not included.

B17b accepts only `WON_OPPORTUNITY`, `ACTIVE_CONTRACT`, and `ACTIVE_RECEIVABLE`. Trend requests contain `metric`, inclusive `from`/`to`, and `granularity` (`DAY`, `WEEK`, or `MONTH`); their response echoes those normalized values and returns ordered `{ periodStart, count, amount }` points. Weeks start on ISO Monday and empty periods are zero-filled. Trend requests are limited to 120 buckets and ten years. Ranking requests contain `metric`, inclusive `from`/`to`, and optional `limit` (default 10, range 1..100); the range is limited to 366 days and items are `{ rank, ownerActorId, count, amount }`, ordered by `amount DESC, count DESC, ownerActorId ASC`.

Only the selected source is authorized and queried. Opportunity trends require unmasked `outcome`, `outcome_time`, `estimated_amount`, and `status`; contract trends require `status`, `order_date`, and `total_amount`; receivable trends require `status`, `received_date`, and `amount`. Rankings additionally require that source's `owner_actor`. A missing capability or field rejects the entire request. Field-policy reads and aggregation share one read-only `REPEATABLE_READ` transaction, and fixed date-range indexes bound each source scan.

B17c supports only one `OPPORTUNITY`, `CONTRACT`, or `RECEIVABLE` source per definition. A definition has a name and optional description, one DATE/DATETIME `timeFieldCode`, `DAY`/`WEEK`/`MONTH` granularity, an optional SELECT/BOOLEAN/USER/DEPARTMENT `dimensionFieldCode`, 1..8 unique `COUNT` or numeric `SUM(fieldCode)` measures, and at most 20 field-unique typed filters. RELATION fields are never returned as filter candidates and are rejected if supplied, because their target records have independent data scopes. Each member may retain at most 100 active definitions per source. Names are trimmed, limited by Unicode code points, normalized with NFKC/lowercase, stored without truncating normalization expansion, and made unique among that member's active definitions for the source through a fixed SHA-256 key. Definition queries accept optional normalized-name `keyword` and `objectType`, with `page` starting at 1 and `size` limited to 100. Updates and deletes require the current `version`; deletion writes `status=DELETED`, and updates can explicitly clear the optional description and dimension.

The catalog contains only active, searchable, readable, unmasked fields for the selected source, including governed option labels and allowed filter operators. Definition get, create, update, and run all revalidate against that current catalog. Definitions are scoped to the creating member as well as tenant and ID; tenant owners and administrators receive no ownership bypass. An active definition is a B05 dependency, so any referenced time, dimension, SUM, or filter field cannot be deactivated or changed from searchable to non-searchable until the definition is deleted.

Run requests contain inclusive `from` and `to` dates. The range is limited to ten years and 120 periods, and the ordered result is limited to 500 `(periodStart, dimension)` rows; exceeding the row limit rejects the complete run rather than truncating it. SELECT and BOOLEAN dimensions return governed labels, while USER and DEPARTMENT dimensions return only their key with a null label. SELECT option keys use `utf8mb4_bin` semantics for filtering, grouping, and stable ordering, so keys such as `A` and `a` remain distinct. The selected source repository applies its established tenant, lifecycle, data-scope, and collaborator rules and aggregates in MySQL. SQL structure comes only from server-owned source/field descriptors; client values remain parameters, and definitions cannot contain SQL, expressions, table names, column names, scripts, or cross-object joins. Definition lookup, field-policy resolution, and aggregation share one read-only `REPEATABLE_READ` snapshot.

## Dashboard preferences and fixed overview

B18a replaces the legacy numeric preference type and opaque string value with one typed current-member resource. Both routes require an active membership and `reports:read`; neither accepts tenant, actor, role, authority, or administrator input.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `GET /api/v1/dashboard/preferences` | `reports:read` | Return the caller's stored defaults, or `CURRENT_MONTH`/`SELF` with no hidden modules and a null version before the first save. |
| `PUT /api/v1/dashboard/preferences` | `reports:read` | Create or optimistic-version-update the caller's complete time, data-scope, and module-display preference. |
| `GET /api/v1/dashboard/overview` | `reports:read` + each independently available source `*:read` | Apply the stored defaults to three fixed KPI/trend cards and matching Top 10 owner rankings; inaccessible projections are returned as opaque `UNAVAILABLE` values. |
| `GET /api/v1/dashboard/performance-targets` | `dashboard-targets:read` | Return one active member's configured monthly targets for one calendar year and metric. |
| `POST /api/v1/dashboard/performance-targets` | `dashboard-targets:write` | Create one versioned member/month/metric target. |
| `PUT /api/v1/dashboard/performance-targets/{targetId}` | `dashboard-targets:write` | Change an existing target amount with its required optimistic-lock version. |
| `DELETE /api/v1/dashboard/performance-targets/{targetId}?version=...` | `dashboard-targets:write` | Delete an existing target with its required optimistic-lock version. |

The request and response fields are `timeRange`, optional `customStartDate`/`customEndDate`, `dataScope`, `hiddenModules`, and `version`. `timeRange` accepts `TODAY`, `CURRENT_WEEK`, `CURRENT_MONTH`, `CURRENT_QUARTER`, `CURRENT_YEAR`, or `CUSTOM`. Only `CUSTOM` accepts dates; both dates are required, ordered, within MySQL's supported date range, and span at most 366 inclusive days. `dataScope` accepts `SELF`, `DEPARTMENT`, `DEPARTMENT_SUBTREE`, or `TENANT`. `hiddenModules` is a complete bounded display-only list whose current sole code is `CUSTOMER_CONTACT_REMINDER`; it cannot select a new aggregation, change overview data, or alter source authorization. A new resource requires a null version; an existing resource requires its current version.

The stored `dataScope` is not an authorization grant. The overview resolves each source object's current capability, data scope, and field policy, then intersects those rules with this presentation preference. Selecting `TENANT` therefore cannot expose a tenant-wide result when any source route grants less access; selecting a department scope under a tenant-wide source grant rebuilds the current member's active department predicate, and a member without a department safely falls back to `SELF`.

The overview response contains `asOfDate`, the selected `timeRange`, resolved inclusive `from`/`to`, `preferredDataScope`, automatic `granularity`, and `cards` plus `rankings` in the stable order `WON_OPPORTUNITY`, `ACTIVE_CONTRACT`, `ACTIVE_RECEIVABLE`. Available cards contain the trend-derived `count`, `amount`, and ordered zero-filled points. Available rankings contain at most ten ranked owner actor IDs with the same server-derived `count` and `amount`, in the fixed B17b order. Ranges through 31 inclusive days use `DAY`, 32–120 use ISO-Monday `WEEK`, and 121–366 use `MONTH`. A card requires its B17b source fields; a ranking additionally requires the source owner field. A missing capability, usable scope, or unmasked field returns `status=UNAVAILABLE` with no totals/points or with an empty ranking list, without exposing a reason. Only expected access denials are converted this way; database and unknown runtime failures fail the whole request. No overview request body, tenant, actor, temporary filter, metric selector, or component definition is accepted.

The overview's required `target` is deliberately narrower than its cards: it currently represents only `ACTIVE_CONTRACT` configured for the caller as a member. It is `AVAILABLE` only for an authenticated caller with `dashboard-targets:read`, a stored/effective `SELF` preference, and an available active-contract card; it sums the configured whole-calendar-month targets touched by the resolved window and uses that card's amount as `actualAmount`. `completionRate` is server-calculated as `actualAmount / targetAmount * 100`, rounded to two decimals. A window with no configured member targets is `EMPTY` and contains no amounts. Missing authority, any non-`SELF` preference, or an unavailable contract card is opaque `UNAVAILABLE` and contains no amounts. This endpoint never derives a department, subtree, or tenant target by adding arbitrary member rows.

Performance-target management accepts only an active target member, `ACTIVE_CONTRACT`, calendar years 2000 through 9999, months 1 through 12, and a positive two-decimal amount up to `99999999999999999.99`. The natural key `(tenant, member, metric, targetYear, targetMonth)` is database-unique. A duplicate create returns `RESOURCE_ALREADY_EXISTS`; update and delete require the current version and return `VERSION_CONFLICT` when stale. Tenant, caller, authority, scope, and aggregation inputs are all server-derived rather than request fields.

## Marketing forms

Tenant administrators configure B19a forms under `/api/v1/marketing-forms`. A form has an internal `name`, public `title`, optional `description`, `submitButtonText` and `successMessage`, a persisted lead `ownerActorId`, an optimistic-lock `version`, and 1–50 ordered fields. Each field contains `fieldCode`, public `label`, optional `placeholder` and `helpText`, `required`, and `sortOrder`. Field type and options in responses are resolved from the current B05 LEAD catalog; clients do not submit or persist copies of them.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `GET /api/v1/marketing-forms?page=1&size=20&status=ACTIVE` | `marketing-forms:read` | Page tenant forms, optionally by `INACTIVE`, `ACTIVE`, or `DELETED`. |
| `GET /api/v1/marketing-forms/{formId}` | `marketing-forms:read` | Read one non-deleted form and its current field metadata. |
| `POST /api/v1/marketing-forms` | `marketing-forms:write` | Create an inactive form; omitted `ownerActorId` defaults to the caller. |
| `PUT /api/v1/marketing-forms/{formId}` | `marketing-forms:write` | Version-replace presentation, owner, and ordered fields. |
| `PUT /api/v1/marketing-forms/{formId}/status` | `marketing-forms:write` | Activate or deactivate using `{ "version": 0, "status": "ACTIVE" }`. |
| `DELETE /api/v1/marketing-forms/{formId}?version=…` | `marketing-forms:write` | Soft-delete an inactive form. |
| `POST /api/v1/marketing-forms/{formId}/submissions/trends` | `marketing-forms:read` | Query the form's historical successful-submission counts. |
| `POST /api/v1/marketing-forms/{formId}/submissions/query` | `marketing-forms:read` + `leads:read` | Page successful submissions whose linked leads are currently visible. |

Activation requires the persisted owner to remain an active member with an effective `leads:write` data scope and `WRITE` access to `lead_name`, `owner_actor`, and every configured field. Every current required LEAD field must be publicly supported, included, and marked required. Built-in public fields are limited to `lead_name`, `phone_number`, `telephone`, `job_title`, `email`, `website`, `description`, and `next_contact_time`; custom `FILE` fields use the separate one-time draft flow, while USER, DEPARTMENT, RELATION, and ADDRESS custom fields are not accepted. An active form blocks deactivation of its referenced B05 field. Deactivation itself does not depend on the owner's current lead access, so a form manager can always shut down a form that has become invalid.

Public endpoints require no caller credential:

| Method and path | Purpose |
| --- | --- |
| `GET /api/v1/open/marketing-forms/{publicId}` | Render an active form with public presentation and current field metadata. |
| `POST /api/v1/open/marketing-forms/{publicId}` or `POST /api/v1/open/marketing-forms/{publicId}/submissions` | Submit configured field values and create a lead through B09. |
| `POST /api/v1/open/marketing-forms/{publicId}/file-drafts` | Create one short-lived anonymous FILE draft and return its one-time upload capability. |
| `POST /api/v1/open/marketing-forms/{publicId}/file-drafts/{draftId}/fields/{fieldCode}/upload-intents` | Create one FILE candidate upload intent using the draft capability header. |
| `PUT /api/v1/open/marketing-forms/{publicId}/file-drafts/{draftId}/fields/{fieldCode}/files/{fileId}/content?version=…` | Upload `application/octet-stream` content for that exact candidate. |
| `GET .../files/{fileId}/upload-status`, `DELETE .../file-drafts/{draftId}` | Read a safe candidate status or permanently withdraw the draft. |

The submission body is `{"idempotencyKey":"browser-retry-1","values":{"lead_name":["Example lead"],"email":["lead@example.com"]}}`. It is a strict whitelist: unknown top-level properties are rejected, keys must exactly match configured fields, and each value is a bounded list used by B09's built-in or custom-field mapping. The canonical public ID contains a tenant locator and random form key, but it is only a lookup handle: tenant and owner are derived from the persisted form, never from the body or trusted headers. POST admission decodes and parses the identifier before regenerating its canonical rate-limit key; the direct route, `/submissions` alias, and equivalent percent-encoded variants share one quota, while malformed or non-canonical decoded paths are rejected. Each public read and submission rechecks current owner membership, the fully resolved B09 `leads:write` scope, and field policy. Submission holds a database lock on the active form and runs the reused B09 creation operation in the same transaction, so an update or deactivation cannot complete and then be followed by a write from the previous configuration, and connection-pool capacity is not doubled by a nested command. B09 validation, uniqueness, and safely wrapped internal failures therefore use only the `MARKETING_FORM_SUBMIT` scene (`6907`). The public form omits tenant, owner, version, and success-message data; a successful submission returns `{ submissionId, accepted: true, idempotencyKey }`, not the created lead or internal owner data. Repeating the exact normalized submission with the same key returns the same receipt; reusing the key with another form version or payload returns a conflict.

The FILE draft response contains required `draftId`, supported `fieldCodes`, `uploadCapability`, and expiry. The opaque capability is held only in browser memory and sent exclusively as `X-Public-Upload-Capability`; it is neither `Authorization` nor a tenant credential and must not be put in a URL, log, analytics payload, or persistent browser storage. Upload intents contain an exact candidate ID, relative upload URL, binary method, non-secret required headers, version, and expiry. Upload/status responses always include the draft, field, file, display name, media type, byte size, version, and `PENDING`, `UPLOADED`, or `FAILED` status. The final submission supplies the draft and exact field/file IDs, and the server rechecks the public form, field, expiry, capability, and uploaded state before binding it to the lead.

Every anonymous POST consumes both the per-form and resolved-source fixed-window quota before controller or database work; exhaustion returns HTTP `429`. Defaults are 60 requests per public form per minute and 20 per source per minute. Bucket maps are bounded and fail closed. The source address uses the trusted-proxy configuration documented for public lead intake, and the in-process quotas are per application replica rather than globally distributed.

Every successful public POST also appends one ledger row in the same transaction as its B09 lead. The insert assigns `submittedTime` with MySQL `UTC_TIMESTAMP(6)` and reloads the persisted row; the ledger stores only tenant, internal form ID, actual form version, lead ID, the scoped idempotency key, a SHA-256 request digest, and that database UTC time. Rejected, rate-limited, or rolled-back attempts create no row. Submitted values, IP addresses, headers, referrers, cookies, device identifiers, and UTM/click identifiers are not stored in this table.

The trend body is `{"from":"2026-07-01","to":"2026-07-31","granularity":"DAY"}`. Dates are inclusive, weeks start on ISO Monday, and `points` contains ascending `{ "periodStart": "2026-07-01", "acceptedCount": 0 }` entries with missing buckets filled. DAY, WEEK, and MONTH are supported; more than 120 buckets or a span over ten years is rejected. Counts are form facts and do not require `leads:read`, inspect lead fields, or decrease after lead conversion, visibility changes, or soft deletion. INACTIVE and DELETED forms retain this history.

The link-query body is `{"page":1,"size":20,"from":"2026-07-01","to":"2026-07-31"}`. `size` is 1–100; `from` and `to` are both omitted for all history or both supplied for an inclusive range of at most 366 days. Items contain only `submissionId`, `leadId`, `formVersion`, and `submittedTime`, ordered by `submittedTime DESC, submissionId DESC`. The caller's current B09 `leads:read` SELF, DEPARTMENT, DEPARTMENT_SUBTREE, TENANT, and collaborator visibility is part of the SQL used for total and pagination. Cross-tenant, soft-deleted, or currently hidden leads are excluded without occupying page positions; therefore a trend count and link-page total need not match.

## OA forms

B20a tenant OA form definitions live under `/api/v1/oa/forms`. Management routes require an active membership plus `oa-forms:read` or `oa-forms:write`; the ordinary available catalog requires both `oa-forms:read` and `oa-requests:submit`. Deleted forms are never readable.

| Route | Authority | Contract |
| --- | --- | --- |
| `GET /api/v1/oa/forms?page=1&size=20&status=ACTIVE` | `oa-forms:read` | Page non-deleted tenant definitions, optionally by `INACTIVE` or `ACTIVE`. |
| `GET /api/v1/oa/forms/{formId}` | `oa-forms:read` | Read one non-deleted definition using current field metadata and access. |
| `GET /api/v1/oa/forms/configuration-options` | `oa-forms:write` | List only active, currently writable non-derived non-`RELATION` input fields and compatible active OA approval definitions for a form editor. |
| `GET /api/v1/oa/forms/available` | `oa-forms:read` + `oa-requests:submit` | List active forms the caller may currently initiate and fully write. |
| `GET /api/v1/oa/forms/available/{formId}` | `oa-forms:read` + `oa-requests:submit` | Render one currently available active form or return not found. |
| `GET /api/v1/oa/forms/available/{formId}/fields/{fieldCode}/candidates?keyword=&page=1&size=20` | `oa-forms:read` + `oa-requests:submit` | Page active tenant members for a configured `USER` field or active departments for a configured `DEPARTMENT` field. The response is only `{id,displayName,valueType}` and returns not found when the form is unavailable to the caller. |
| `GET /api/v1/oa/forms/available/{formId}/approval-selections/{selectionCode}/candidates?keyword=&page=1&size=50` | `oa-forms:read` + `oa-requests:submit` | Search and page active tenant members for a configured `APPLICANT_SELECT` approval step on a form currently available to the caller. |
| `GET /api/v1/oa/announcements/audience-candidates?type=MEMBER|DEPARTMENT_SUBTREE&keyword=&page=1&size=50` | `oa-announcements:write` | Search and page active tenant members or active departments that may be used as one OA announcement audience selector. |

Managed announcement responses return each persisted selector's safe `displayName` when applicable and the closed `allowedActions` projection. Clients preserve an existing selector only from that response or add it from the candidate endpoint; they do not synthesize actor or department IDs, or infer allowed lifecycle transitions from status.
| `POST /api/v1/oa/forms` | `oa-forms:write` | Create an inactive form. |
| `PUT /api/v1/oa/forms/{formId}` | `oa-forms:write` | Version-replace code, presentation, approval reference, and fields. |
| `PUT /api/v1/oa/forms/{formId}/status` | `oa-forms:write` | Activate or deactivate with `{ "version": 0, "status": "ACTIVE" }`. |
| `DELETE /api/v1/oa/forms/{formId}?version=…` | `oa-forms:write` | Soft-delete an inactive form. |

Create uses `code`, `name`, optional plain-text `description`, `approvalDefinitionId`, and 1–100 fields. Update adds `version`. Each field is exactly `{ "fieldCode": "request_title", "required": true, "sortOrder": 10 }`; type, display name, description, options, status, searchability, and the caller's `accessLevel`/`maskType` are rebuilt from the current B05 catalog in the response. HIDDEN fields are omitted. `approvalSelections` is the closed `{code,name,mode}` projection of the selected form's `APPLICANT_SELECT` steps; the matching candidate endpoint must still be used before a code is submitted. `RELATION` is deliberately absent from the configuration catalog because OA has no governed owning-module relation contract and rejects non-empty relation values. Codes are tenant-unique among non-deleted forms after NFKC/lowercase normalization, and a tenant has at most 200 such forms.

`request_title` must be present and required. `form_code`, `applicant_actor`, `applicant_department`, and `submitted_time` are server-derived and cannot be configured as inputs; FILE is not supported. Activation requires an active same-tenant `OA_REQUEST` approval definition, and all its condition fields must be contained in the form inputs plus those four server facts. An available form additionally requires the approval initiator scope to admit the caller and every configured field to remain active and writable. Active forms block referenced-field deactivation or searchability removal and block approval deactivation or incompatible condition changes. Cross-tenant field, approval, and form references use not-found semantics.

## OA requests

B20b immutable requests live under `/api/v1/oa/requests`. Preview and submit require an active membership plus `oa-requests:submit`; list and detail require `oa-requests:read`. `oa-requests:manage` widens read visibility to the tenant but grants no B11 action, withdrawal, or cancellation authority.

| Route | Authority | Contract |
| --- | --- | --- |
| `POST /api/v1/oa/requests/preview` | `oa-requests:submit` | Revalidate the active form, current B05 `WRITE` access, server facts, and current B11 route without persisting. |
| `POST /api/v1/oa/requests` | `oa-requests:submit` | Atomically persist one immutable request and its B11 approval instance. |
| `GET /api/v1/oa/requests?formId=…&status=PENDING&page=1&size=20` | `oa-requests:read` | Page only requests currently visible through participation or OA management; size is 1–200. |
| `GET /api/v1/oa/requests/{requestId}` | `oa-requests:read` | Read one visible request with current field policy and current approval status. |

Preview accepts `formId`, `formVersion`, `values`, and optional `selectedApprovers`. Submit adds a required, trimmed, at-most-128-character `idempotencyKey` and optional `watcherActorIds`. Unknown top-level properties—including tenant, applicant, department, or a separate `facts` map—are rejected. `values` must exactly match configured input codes; server fields cannot be supplied. Preview is advisory: submit locks and revalidates current membership, form, fields, access, definition, and dynamic assignees.

The idempotency boundary is `(tenant, applicant, key)`. Equivalent concurrent retries return the same request and approval IDs; a retry that changes the form, version, normalized values, selected route, or watchers returns a conflict. The server-owned B11 key is `oa:<requestId>`, and public B11 submission rejects both `oa:` and `stage:` namespaces. The submit command persists the request, resolves approval facts from that stored row, creates the B11 instance, and links it back in one transaction and database connection.

Ordinary visibility is the union of applicant, any historical task assignee, submission-time manager, and watcher. A current active member with `oa-requests:manage` sees all tenant requests. The SQL predicate is applied before total and page slicing; same-department membership alone grants nothing. `approvalStatus` is read from B11 and is never mirrored on the request. Request values and all B11 OA snapshots omit `HIDDEN` fields and mask `PARTIAL` fields under the caller's current OA policy. B11 review, transfer, batch, withdrawal, and cancellation continue through existing approval endpoints. B15 exposes OA review work only as the existing `APPROVAL_PENDING` kind; OA submission never creates or changes a B21 Work task.

Request responses keep only `id`, `formId`, `formVersion`, `approvalInstanceId`, `approvalStatus`, `values`, and root `version` in the structural envelope. Form code, applicant, department, request title, and submitted time appear only through field-filtered `values`; raw duplicates cannot bypass a hidden or masked field. The same rule replaces or omits OA approval `title`, `applicantActorId`, and `submittedTime` when their corresponding current facts are masked or hidden. Generic B11 keyword search excludes OA instances because it operates on the raw title column; use participant/status/form filters instead. Because B15 summaries must be unmasked, an OA pending item is omitted from the worklist when the caller cannot currently read `request_title` without masking.

### OA Request collaboration attachments

B29d adds the closed ordinary attachment target token `oa-request`, rooted at
`/api/v1/attachment-targets/oa-request/{requestId}/attachments`. These are ordinary B24
relations, not OA form `FILE` values: requests carry no `fieldCode`, and attachment changes do
not alter submitted facts, the submission digest, approval conditions, route snapshots, or
`crm_oa_request_file_value`.

| Route family | Required authority and predicate | Contract |
| --- | --- | --- |
| List/get/download | `files:read` + `oa-requests:read` + active membership + current request visibility | Applicant, historical task assignee, instance manager, watcher, or a current `oa-requests:manage` member can read only the current `AVAILABLE` set. |
| Create intent/upload/status/withdraw | `files:write` + `oa-requests:read` + `oa-requests:collaborate` + active membership | The request must be `PENDING`; only its applicant, a currently pending task assignee, or an immutable instance-manager snapshot member can collaborate. Historical assignees, watchers, same-department members, and tenant OA managers cannot write merely because they can read. |

The target transaction serializes `tenant -> approval_instance -> oa_request -> attachment
relation/file`; membership is revalidated with a current locking read after the tenant lock, and
the approval tasks are rehydrated with a locking current read after the approval root is locked.
This prevents a pre-lock OA visibility read from retaining a historical pending assignee under
MySQL `REPEATABLE_READ`. A terminal B11 action, sorted batch action, withdrawal, or cancellation atomically withdraws every
non-`AVAILABLE` relation and schedules the normal B24 cleanup work. The exact remaining
`AVAILABLE` set is frozen: later mutation is not found, while its normal authorized downloads
remain available. Attachment workers use the same root order and cannot publish an old candidate
after terminal cutoff.

## OA announcements

B20c internal plain-text announcements live under `/api/v1/oa/announcements`. Management requires an active membership plus `oa-announcements:write`; inbox, unread, and receipt routes require `oa-announcements:read`. The two capabilities do not imply each other.

| Route | Authority | Contract |
| --- | --- | --- |
| `GET /api/v1/oa/announcements?status=DRAFT&page=1&size=20` | `oa-announcements:write` | Page root-only non-deleted tenant summaries with batch recipient/read counts; selectors are omitted. |
| `GET /api/v1/oa/announcements/{announcementId}` | `oa-announcements:write` | Read one managed announcement, selectors, and aggregate counts. |
| `POST /api/v1/oa/announcements` | `oa-announcements:write` | Create a version-0 draft. |
| `PUT /api/v1/oa/announcements/{announcementId}` | `oa-announcements:write` | Version-replace draft title, body, and selectors. |
| `POST /api/v1/oa/announcements/{announcementId}/publish` | `oa-announcements:write` | Publish with `{ "version": 0 }` and atomically snapshot recipients. |
| `POST /api/v1/oa/announcements/{announcementId}/schedule` | `oa-announcements:write` | Create or replace one future publication generation using `{ "version": 0, "scheduledPublishTime": "…+08:00" }`. |
| `POST /api/v1/oa/announcements/{announcementId}/cancel-schedule` | `oa-announcements:write` | Cancel the current scheduled generation using `{ "version": 0 }`. |
| `POST /api/v1/oa/announcements/{announcementId}/withdraw` | `oa-announcements:write` | Withdraw one published version. |
| `DELETE /api/v1/oa/announcements/{announcementId}?version=…` | `oa-announcements:write` | Soft-delete a draft or withdrawn announcement. |
| `GET /api/v1/oa/announcements/mine?page=1&size=20` | `oa-announcements:read` | Page only currently readable published snapshots delivered to the caller. |
| `GET /api/v1/oa/announcements/mine/{announcementId}` | `oa-announcements:read` | Read one currently visible delivered announcement. |
| `GET /api/v1/oa/announcements/unread-count` | `oa-announcements:read` | Count unread, currently readable published snapshots. |
| `POST /api/v1/oa/announcements/mine/{announcementId}/read` | `oa-announcements:read` | Insert a server-UTC receipt or return the original receipt time. |

Create accepts a nonblank title of at most 200 Unicode code points, a nonblank body of at most 20,000 code points, and 1–10,000 unique typed selectors. Selector shapes are exactly `{ "type": "TENANT" }`, `{ "type": "DEPARTMENT_SUBTREE", "departmentId": 12 }`, or `{ "type": "MEMBER", "memberActorId": 34 }`; irrelevant IDs and unknown properties are rejected. Cross-tenant or inactive targets use not-found semantics. Content is returned as plain text and is never interpreted as HTML or Markdown.

### OA announcement attachments

The closed target token is `oa-announcement`; it is not an OA inbox-copy or EMAIL payload. The standard attachment routes are rooted at `/api/v1/attachment-targets/oa-announcement/{announcementId}/attachments`:

| Routes | Required authority and state | Contract |
| --- | --- | --- |
| `POST /upload-intents`, `PUT /{fileId}/content`, `GET /{fileId}/upload-status`, `DELETE /{fileId}?version=…` | Active member, `files:write` + `oa-announcements:write`, root is `DRAFT` | Create, upload, inspect status, or withdraw one draft attachment. |
| `GET`, `GET /{fileId}` | Draft manager: `files:read` + `oa-announcements:write`, root is `DRAFT`; or published recipient: `files:read` + `oa-announcements:read`, active membership, root is `PUBLISHED`, and exact immutable recipient-snapshot admission | List or fetch available attachment metadata without widening OA management or inbox visibility. |
| `GET /{fileId}/content` | The same read matrix as metadata | Stream one available file. A recipient never receives a copied relation, URL, or EMAIL attachment. |

`SCHEDULED`, `PUBLISHED`, `WITHDRAWN`, and `DELETED` reject every attachment mutation. Schedule, immediate publication, and due publication freeze a maximum of 100 active relations; every file must be `AVAILABLE`, otherwise publication does not proceed. Withdrawal and deletion close active relations through the parent lifecycle. Cross-tenant targets, a file/target mismatch, missing snapshot admission, or a caller without `files:read` use the same not-found result. B28 EMAIL remains attachment-free and URL-free.

The management page, inbox page/detail, and mark-read visibility lock use root-only projections and never hydrate selector child tables. Complete selector hydration is reserved for managed detail and mutations that need the aggregate. HTTP security applies the same split before controller dispatch: the three inbox GET shapes and receipt POST require `oa-announcements:read`, while all remaining announcement paths require `oa-announcements:write`; application guards independently repeat those checks with current membership.

Publication locks tenant organization changes and the root, revalidates selectors, resolves only active same-tenant memberships, de-duplicates overlaps, and persists at most 10,000 recipients. Overflow and an empty resolved audience fail the complete transaction. Later joins or department moves never add recipients, but current membership suspension still closes reads. `PUBLISHED` content and selectors are immutable; withdrawal removes the item from inbox and unread queries. Manager output exposes only `recipientTotal` and `readTotal`, never recipient profiles or reading duration.

## Approvals and stage workflows

Approval capabilities are `approvals:read`, `approvals:submit`, `approvals:review`, and `approvals:manage`. Stage capabilities are `workflows:read`, `workflows:write`, and `workflows:manage`. Every route also requires an active membership. General public approval preview/submission accepts `CUSTOMER`, `CONTACT`, `LEAD`, `OPPORTUNITY`, `CONTRACT`, `INVOICE`, and `RECEIVABLE`; `OA_REQUEST` definitions are configured through the same B11 APIs, but OA preview/submission must use the B20b form-bound endpoints above. Existing instance query and action endpoints support OA after applying its participant visibility adapter. Stage runtime remains limited to `CUSTOMER`, `CONTACT`, `LEAD`, and `OPPORTUNITY`. The referenced business record must remain visible under its owning domain. Public preview/submission condition facts are re-read from that business aggregate; supplied `facts` select additional snapshot fields but cannot replace their persisted values, and every supplied code requires current B05 `WRITE` access. Applicant-select candidates are obtained only from the scoped candidate endpoints below; the general member directory is not an authority source, and submission/advance resolves current eligible membership again. Stage gates merge the same authoritative snapshot with B05-`WRITE`-validated pending stage values, while configuration rejects a stage-required/approval-condition field union above 100 and generated gate titles never exceed 200 characters. `GET /stage-workflows/applicable` echoes the resolved `objectType` and `recordId`; each active workflow has `canStart` and, when false, a stable `blockerCode`. A runtime record contains the server-renderable writable field definition (name, type, required flag, enumerated values, current values and mask state), gate applicant-select choices, and a minimal stage-file attachment projection; its `allowedActions` and status are required closed values. Approval and stage responses omit `HIDDEN` fields and apply current `PARTIAL` or `FULL` masks. Approval detail, actions, batch actions, withdrawal, cancellation, bucket queries, and pending counts all reapply current record visibility. Bucket pagination scans bounded repository chunks, removes hidden records before page slicing, and reports the total number of visible matches. Role steps resolve assignments only while both the referenced tenant role and each resulting membership are active; every resolved step and manager fallback is capped at 100 unique members.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `GET /api/v1/approval-definitions/fields?objectType=…` | `approvals:manage` | List active searchable typed routing fields. |
| `POST /api/v1/approval-definitions/query`, `GET /{definitionId}` | `approvals:manage` | Page or read tenant definitions. |
| `POST /api/v1/approval-definitions`, `PUT /{definitionId}`, `PUT /{definitionId}/status` | `approvals:manage` | Create, version-replace, activate, deactivate, or soft-delete a definition. |
| `POST /api/v1/approval-definitions/{definitionId}/preview` | `approvals:submit` | Resolve the same immutable route used by submission. |
| `GET /api/v1/approvals/subjects/{objectType}/{subjectId}/start-options/{definitionId}/selections/{selectionCode}/candidates` | `approvals:submit` | Page server-authorized candidates for one applicant-select start option; the definition, visible business record, current applicant scope and selection are all rechecked. |
| `POST /api/v1/approvals` | `approvals:submit` | Idempotently submit a visible non-OA record and persist its resolved route; caller keys are trimmed once and must not use the case-insensitive reserved `stage:` or `oa:` namespaces. |
| `GET /api/v1/approvals/{instanceId}`, `POST /query`, `GET /pending-count` | `approvals:read` | Read one complete participant/manager task timeline, page root-only caller-bucket summaries, or count pending tasks. |
| `POST /api/v1/approvals/{instanceId}/actions`, `/actions/batch` | `approvals:review` | Approve, reject, transfer, or atomically process up to 50 unique sorted instances; the batch response contains root summaries without task history. |
| `POST /api/v1/approvals/{instanceId}/withdraw` | `approvals:submit` | Withdraw an unacted pending submission as its applicant. |
| `POST /api/v1/approvals/{instanceId}/cancel` | `approvals:review` | Cancel a pending instance as a submission-time snapshotted manager or a caller with global approval management authority. |
| `POST /api/v1/stage-workflows/query`, `GET /applicable`, `GET /{workflowId}` | `workflows:read` | Page definitions, or list active definitions with an explicit per-workflow startability decision for a visible record. |
| `POST /api/v1/stage-workflows`, `PUT /{workflowId}`, `PUT /{workflowId}/status` | `workflows:manage` | Create, replace an unreferenced definition, or version-change status. |
| `GET /api/v1/stage-workflows/configuration-options?objectType=…`, `/configuration-options/candidates` | `workflows:manage` | Get the current caller's configurable object/field/active-approval catalog and page active member or department values. Field entries expose B05 write state, required-field eligibility, and a stable blocker; clients preserve the complete detail baseline for replacement. |
| `POST /api/v1/stage-workflows/{workflowId}/records/{recordId}` | `workflows:write` | Start an applicable workflow for a writable business record after locking the object catalog and reloading the current definition. |
| `GET /api/v1/stage-workflow-records/{workflowRecordId}` | `workflows:read` | Read the canonical runtime record after reapplying business visibility. |
| `GET /api/v1/stage-workflow-records/{workflowRecordId}/advance-options/{selectionCode}/candidates` | `workflows:write` | Page server-authorized candidates for the current stage gate's applicant-select option. |
| `GET /api/v1/stage-workflow-records/{workflowRecordId}/writable-fields/{fieldCode}/candidates` | `workflows:write` | Page candidates constrained by the visible writable record, current active stage, field type and caller scope; supports actor, department and declared-target relation IDs only. |
| `POST /api/v1/stage-workflow-records/{workflowRecordId}/writable-fields/{fieldCode}/file-drafts` | `workflows:write` + `files:write` | Issue a FILE draft only after rechecking that exact active-stage field. Upload uses the returned draft; advance consumes only its exact selected clean file IDs. |
| `POST /api/v1/file-field-drafts/{draftId}/fields/{fieldCode}/upload-intents`, `PUT .../files/{fileId}/content`, `GET .../upload-status` | `files:write` + current field write policy | Create a typed upload instruction, upload one staged candidate, or read its minimal typed status. Stage-origin drafts remain bound to their exact workflow record. |
| `PUT /api/v1/stage-workflow-records/{workflowRecordId}/advance`, `/outcome` | `workflows:write` | Capture required fields/tasks, open or resolve an approval gate, advance, or fail. |
| `GET`, `POST /api/v1/stage-workflow-records/{workflowRecordId}/comments` | `workflows:read`, `workflows:write` | Page or create comments under the record's current data scope. |
| `DELETE /api/v1/stage-workflow-records/{workflowRecordId}/comments/{commentId}?version=…` | `workflows:write` | Soft-delete the caller's own current-version comment. |

Stage-required field values must remain active and searchable (except governed `FILE`) and must normalize to a non-empty value list. `StageAdvanceRequest.fieldValues` is scalar-only: `USER`, `DEPARTMENT`, `RELATION`, `ADDRESS`, and `FILE` use their typed maps, with typed IDs and structured address parts; an ADDRESS field exposes its editable current value through `currentAddress`, not an encoded scalar. FILE uses `fileFieldDraftId` plus `fileFields`; stage-origin drafts are record-scoped and their typed upload intent contains exact file ID, URL, method, headers and expiry. The service rechecks every submitted actor's active profile/membership, department status, relation target visibility, scoped file draft, exact draft-field file set, expiry and clean evidence. Configuration candidates accept only the `MEMBER` or `DEPARTMENT` enum, and a workflow detail exposes `allowedStatuses` only alongside its `CHANGE_STATUS` capability; writes still enforce transition and version. Unsupported relation fields are returned as `UNSUPPORTED` with `RELATION_TARGET_UNDECLARED` and cannot be configured for a stage. A runtime record's `allowedOutcomes` is the submit authority for outcome values; at present it advertises only `FAILED`, including its display label and remark limits. A referenced stage field cannot be deactivated or changed to non-searchable. An active workflow may only be updated, started, or activated while every referenced approval definition is active. Internal approval gates use the reserved `stage:` idempotency namespace, which public approval submission cannot claim. Approval batch responses are always atomic: `applied=true` means every item persisted, while `applied=false` means no item changed and the per-item required `status`/`errCode` explain the abort. Transfer targets are validated by the service against the action and current tenant membership; clients do not send an advisory consistency flag. Null elements in fact, condition, action, stage, and task collections are rejected as `PARAMETER_INVALID` rather than reaching domain snapshot construction.

Definition steps use `MEMBER`, `ROLE`, or `APPLICANT_SELECT`; modes are `SEQUENTIAL`, `ALL`, and `ANY`; empty resolution uses `AUTO_APPROVE` or configured `MANAGERS`; repeat behavior is `ONCE` or `EVERY_STEP`. Instance status is `PENDING`, `APPROVED`, `REJECTED`, `WITHDRAWN`, or `CANCELLED`. Query buckets are `PENDING`, `PROCESSED`, `SUBMITTED`, `WATCHING`, and manager-only `ALL`. Every mutation requires the current optimistic-lock `version`. Stage records are `ACTIVE`, `AWAITING_APPROVAL`, `SUCCEEDED`, or `FAILED`; success is reached by completing the last stage, while only failure is set explicitly.

## Contacts

Contact capabilities and data scopes are independent from customer capabilities: `contacts:read`, `contacts:export`, `contacts:write`, `contacts:delete`, and `contacts:assign` each resolve their own `SELF`, department, or tenant predicate. Ordinary reads may include explicit collaboration; export is owner-derived and must intersect ordinary read visibility. Contacts use the CONTACT field catalog, including built-in `job_title` and `next_contact_time`, saved views, layout, tags, collaborators, hierarchy, personal stars, and optimistic version in the same bounded form as customers. The optional `customerId` on create or update requires the caller to hold `customers:read` and to see the referenced customer under its data scope; it is omitted from output when the current customer visibility no longer permits it. An update that explicitly includes `customerId: null` removes the relation, while omitting the property leaves it unchanged; similarly, explicitly including `nextContactTime: null` clears that optional time. Contact saved-view conditions for `customer` and `parent_contact` accept only `EQ` or `IN`, and every referenced customer or parent contact must be visible under the caller's respective current scope. Other relation operators are rejected rather than exposing a relationship oracle.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `GET /api/v1/contacts` | `contacts:read` | Page visible active non-pool contacts with optional field-aware `keyword`, typed saved view, direct literal name-prefix, owner, positive `customerId`, star filters, and documented server-owned sort field/direction. `customerId` additionally proves exact customer visibility under `customers:read` and requires an unmasked Contact `customer` field before the SQL count/page predicate. With `activities:read`, each `ContactResponse` may include `lastActivityTime` and `lastActivityContent` from the newest ACTIVE non-system activity whose direct subject is that returned contact; both fields are omitted otherwise. |
| `POST /api/v1/contacts/export/csv` | `contacts:export` + `contacts:read` | Synchronously download a fully generated bounded CSV for the intersection of owner-derived export scope and ordinary read visibility. |
| `PUT /api/v1/contacts/batch/owner` | `contacts:assign` | Atomically transfer 1–100 distinct visible contacts to one active member within the resolved assignment scope. Every target carries its observed version; owner field policy, target visibility, membership, and scope are rechecked before any contact changes. |
| `POST /api/v1/contacts/batch/delete` | `contacts:delete` | Atomically soft-delete 1–100 distinct visible contacts with observed versions. The server validates all hierarchy dependencies, targets, and versions before withdrawing any attachment or file field. |
| `POST /api/v1/contacts` | `contacts:write` | Create a versioned contact; a different owner or collaborators additionally require `contacts:assign`. |
| `GET /api/v1/contacts/{contactId}` | `contacts:read` | Get a field-filtered visible contact. |
| `PUT /api/v1/contacts/{contactId}` | `contacts:write` | Version-update details (including job title and next-contact time), optional customer link (`customerId: null` unlinks), addresses, and custom values. |
| `DELETE /api/v1/contacts/{contactId}?version=…` | `contacts:delete` | Soft-delete a contact with no active child contacts. |
| `PUT /api/v1/contacts/{contactId}/owner`, `/collaborators` | `contacts:assign` | Version-change the owner or active-tenant collaboration set. |
| `PUT /api/v1/contacts/{contactId}/parent` | `contacts:write` | Version-set or clear a visible parent while rejecting cycles. |
| `GET /api/v1/contacts/tags`, `POST /duplicate-check`, `GET /nearby`, `GET /summaries` | `contacts:read` | Perform only contact-visibility-scoped auxiliary reads. |
| `POST /api/v1/contacts/batch/tags`, `PUT /tags`, `/state` | `contacts:write` | Versioned bounded mutations. |
| `PUT /api/v1/contacts/{contactId}/star` | `contacts:read` | Idempotently update the caller's personal star. |

Contact list sorting is a fixed server contract: `sortField` accepts `UPDATED_TIME`, `CREATED_TIME`, `NAME`, or `OWNER_ACTOR`, and `sortDirection` accepts `ASC` or `DESC`. Omitting both retains `UPDATED_TIME DESC, id DESC`; each non-default order follows with `id` in the same direction for stable pagination. Physical database columns are mapped from the server enum, never request text, and a non-default field requires the caller to retain unmasked read access to its matching contact field.

Contact CSV export accepts only ordered `fieldCodes`, optional `keyword`, `name`, `ownerActorId`, `customerId`, `starred`, and the caller-owned `savedViewId`; unknown JSON properties are rejected, and it does not accept pagination, sort, tenant, separator, encoding, locale, timezone, filename, SQL, or object-type parameters. It excludes pool/deleted records plus tags, addresses, members, customer/parent relations, attachments, and FILE values. Customer filters and saved-view Customer targets require `customers:read`, current exact customer visibility, and unmasked Contact `customer`; saved-view parent targets require current Contact read visibility. It accepts 1–50 current active/readable/unmasked scalar field codes of types `TEXT`, `TEXTAREA`, `SELECT`, `DATE`, `DATETIME`, `INTEGER`, `DECIMAL`, `BOOLEAN`, `EMAIL`, `PHONE`, or `URL`; unknown, inactive, hidden, masked, duplicate, or unsupported fields reject the whole request. The completed body is UTF-8 BOM RFC 4180 CSV with CRLF, stable code headers, formula hardening, `contact_id ASC`, a 5,001-row rejection probe, and a 16 MiB pre-body limit. It returns `Content-Type: text/csv;charset=UTF-8`, `Content-Disposition: attachment; filename="contacts.csv"`, `Cache-Control: no-store`, and `X-Content-Type-Options: nosniff`; it creates no artifact, attachment, job, history, receipt, or download token.

## Contact pools

Contact-pool configuration and current membership are separate from customer pools. `contact-pools:manage` controls configuration; `contact-pools:read`, `:release`, `:claim`, `:assign`, and `:delete` control member-facing pool actions. Release additionally requires `contacts:write` under its own data scope. Eligibility, daily claim limits, previous-owner cooldown, ordered transfer locks, version checks, pool membership uniqueness, and fair automatic-release cursor processing all use the same bounded semantics as customer pools without widening contact or customer visibility.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `GET/POST /api/v1/contact-pools`, `GET/PUT/DELETE /api/v1/contact-pools/{poolId}` | `contact-pools:manage` | Manage versioned tenant pool configuration. |
| `GET /api/v1/contact-pools/catalog` | `contact-pools:read` | List active pools accessible to the current member. |
| `POST /api/v1/contact-pools/{poolId}/transfer` | `contact-pools:manage` | Move all memberships to another active pool under ordered locks. |
| `GET /api/v1/contact-pools/{poolId}/contacts[/{contactId}]` | `contact-pools:read` | Read field-filtered in-pool contacts. |
| `POST /api/v1/contact-pools/{poolId}/contacts/release` | `contact-pools:release` + `contacts:write` | Move up to 100 visible active contacts into a pool atomically. |
| `POST .../claim`, `POST .../assign`, `DELETE .../contacts` | respective `contact-pools:*` capability | Claim, assign, or soft-delete bounded pool contacts. |

## Field customization

Field metadata is tenant scoped. The supported object types are `LEAD`, `CUSTOMER`, `CONTACT`, `PRODUCT`, `OPPORTUNITY`, `CONTRACT`, `RECEIVABLE`, `RECEIVABLE_PLAN`, `RETURN_VISIT`, and `INVOICE`.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `GET /api/v1/fields/objects` | `fields:read` | List stable customizable object types. |
| `GET /api/v1/fields/objects/{objectType}` | `fields:read` | List active field metadata; `includeInactive=true` additionally requires `fields:write`. |
| `POST /api/v1/fields/objects/{objectType}` | `fields:write` | Create an active custom field with a server-generated immutable code. |
| `PUT /api/v1/fields/objects/{objectType}/{fieldId}` | `fields:write` | Update mutable metadata by version; uniqueness is immutable, and disabling search is rejected while a saved view references the field. |
| `DELETE /api/v1/fields/objects/{objectType}/{fieldId}?version=…` | `fields:write` | Deactivate an unreferenced custom field without deleting its definition. |
| `GET /api/v1/fields/objects/{objectType}/layout` | `fields:read` | Read the current member's layout intersected with the active field catalog, or an unpersisted safe default. |
| `PUT /api/v1/fields/objects/{objectType}/layout` | `fields:read` | Create or version-update the current member's layout. |
| `GET /api/v1/fields/roles/{roleId}/objects/{objectType}/access` | `fields:write` + `authorization:read` | Read one tenant role's field policy. |
| `PUT /api/v1/fields/roles/{roleId}/objects/{objectType}/access` | `fields:write` + `authorization:write` + `authorization:assign` | Atomically replace an active custom role's delegable field policy. |

Field types are `TEXT`, `TEXTAREA`, `SELECT`, `MULTI_SELECT`, `DATE`, `DATETIME`, `INTEGER`, `DECIMAL`, `BOOLEAN`, `EMAIL`, `PHONE`, `URL`, `FILE`, `USER`, `DEPARTMENT`, `RELATION`, and `ADDRESS`. Each returned field includes the exact server-determined `filterOperators` usable for a saved-view condition, including each operator's accepted minimum and maximum number of values; clients must select only from that array. Select types require one to 100 stable options; other types reject options. Existing option values cannot be removed, and a field's unique flag cannot change after creation. A field cannot be deactivated while any member layout or saved view references it, and a searchable field cannot become non-searchable while a saved view references it. A layout accepts at most 200 active field codes, widths from 80 to 1000, and never permits one field to be both hidden and pinned.

Field-definition commands, layout writes, and saved-view writes for the same tenant and object type are database-serialized. A concurrent layout or view write that starts while a lifecycle change is in progress waits for it, then validates against the committed field catalog; it cannot persist a dependency on a field that has just become inactive or non-searchable.

## Saved views

Saved views are current-member-owned, bounded typed filters for one current object type. They never contain executable expressions, tenant or actor identity, or unvalidated field data. Each saved condition also has an explicit `exposed` flag for browser-controlled filter presentation; it is persistent view metadata and is never accepted by temporary record-query conditions or translated into SQL. List, create, update, and default operations return the named `SavedViewResponse` with the complete filter baseline and current version; deletion returns an explicit empty success response. The current field catalog and caller field access are revalidated on every read and write.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `GET /api/v1/saved-views?objectType=…&includeHidden=false` | `saved-views:read` | List the caller's current-object views. |
| `POST /api/v1/saved-views` | `saved-views:write` | Create one current-member typed filter view. |
| `PUT /api/v1/saved-views/{viewId}` | `saved-views:write` | Version-update one caller-owned view. |
| `PUT /api/v1/saved-views/{viewId}/default?version=…` | `saved-views:write` | Set one caller-owned view as the default for its object type. |
| `DELETE /api/v1/saved-views/{viewId}?version=…` | `saved-views:write` | Delete one caller-owned view. |

Field access values are `READ` or `WRITE`; omission represents `HIDDEN`. Read masking is `NONE`, `PARTIAL`, or `FULL`, while writable fields must use `NONE`. The tenant owner dynamically has unmasked `WRITE` access to every active field. Other roles merge only explicit access, and an inactive member or absent policy fails closed. This field policy narrows access after route/use-case authorization; it does not grant a capability.

Example custom field request:

```json
{
  "displayName": "Customer segment",
  "fieldType": "SELECT",
  "required": false,
  "unique": false,
  "searchable": true,
  "displayOrder": 20,
  "options": [
    {"value": "enterprise", "label": "Enterprise", "order": 10},
    {"value": "growth", "label": "Growth", "order": 20}
  ]
}
```

## Saved views

Saved views belong to the verified current member and object type. No route accepts an actor or tenant identifier.

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `GET /api/v1/saved-views?objectType=…&includeHidden=false` | `saved-views:read` | List current-member views that remain valid against active searchable fields. |
| `POST /api/v1/saved-views` | `saved-views:write` | Create a typed personal view. |
| `PUT /api/v1/saved-views/{viewId}` | `saved-views:write` | Update name, conditions, order, and visibility by version. |
| `PUT /api/v1/saved-views/{viewId}/default?version=…` | `saved-views:write` | Make this the member's only default for its object. |
| `DELETE /api/v1/saved-views/{viewId}?version=…` | `saved-views:write` | Delete the member-owned view by version. |

A view has at most 50 conditions and may reference each active searchable field once. Operators are `EQ`, `NOT_EQ`, `CONTAINS`, `NOT_CONTAINS`, `IS_EMPTY`, `IS_NOT_EMPTY`, `GT`, `GTE`, `LT`, `LTE`, `IN`, and `BETWEEN`; compatibility is checked against the field type. `NOT_EQ` follows SQL inequality and excludes records whose field has no value, while `NOT_CONTAINS` treats a missing value as containing no searched text and therefore includes it; use `IS_EMPTY` when absence itself is the predicate. Each condition has at most 20 values of at most 500 characters. Reads and default selection revalidate stored conditions, so stale metadata is never returned for execution. The stored structure is metadata only and cannot contain SQL, scripts, table names, or executable predicates.

```json
{
  "objectType": "CUSTOMER",
  "name": "Enterprise customers",
  "conditions": [
    {"fieldCode": "cf_example", "operator": "EQ", "values": ["enterprise"]}
  ],
  "sortOrder": 10,
  "hidden": false
}
```

## Embedded pages

| Method and path | Authority | Purpose |
| --- | --- | --- |
| `GET /api/v1/embedded-pages/configuration-options` | `embedded-pages:read` | Return the server-defined business-object, client-platform, and lifecycle-status choices for the current tenant member. |
| `GET /api/v1/embedded-pages` | `embedded-pages:read` | Page tenant configuration; optional filters are `objectType`, `platform`, and `status`. |
| `POST /api/v1/embedded-pages` | `embedded-pages:write` | Create an `INACTIVE` page. |
| `PUT /api/v1/embedded-pages/{pageId}` | `embedded-pages:write` | Update the page and `ACTIVE`/`INACTIVE` status by version. |
| `DELETE /api/v1/embedded-pages/{pageId}?version=…` | `embedded-pages:write` | Delete only an inactive page by version. |
| `GET /api/v1/embedded-pages/objects/{objectType}/{recordId}?platform=…` | `embedded-pages:read` | Return typed rendered active links for `WEB` or `MOBILE`. |

The configuration page, create, update, and active-render endpoints all publish named OpenAPI responses: `EmbeddedPagePageResponse`, `EmbeddedPageResponse`, `EmbeddedPageConfigurationOptionsResponse`, and `RenderedEmbeddedPageResponse`. Clients must use the configuration response instead of maintaining local object, platform, or status catalogs. An embedded-page template is at most 1000 characters, must be an HTTPS URL without user information, and may contain only the `{recordId}` placeholder. The server replaces that token with the positive numeric path ID and returns the URL; it never resolves, opens, proxies, previews, or otherwise fetches the target.

### Organization request examples

Bootstrap the first member in the verified current tenant:

```json
{
  "displayName": "First Member",
  "email": "first@example.com",
  "phoneNumber": "+12025550123"
}
```

Submit a request to join another already initialized tenant:

```json
{
  "targetTenantId": 42,
  "targetDepartmentId": 7,
  "message": "Please add me to the sales team."
}
```

## Deprecated routes

Six stage-workflow record routes are still served under the workflow collection they used to live
in. Each one duplicates a route on `/api/v1/stage-workflow-records/**` and delegates to the same
handler, so the two forms behave identically; the duplicate exists only so existing callers keep
working while they move.

They are a migration shim with an end date, not a second supported surface. **All six are removed
in 2.0.0.** Until then they stay marked `deprecated = true` in the OpenAPI document, and
`DeprecatedRouteRetirementTest` holds the inventory below so a seventh cannot appear unnoticed and
so removing one is a deliberate edit rather than a silent drift.

| Deprecated route | Replacement |
| --- | --- |
| `GET /api/v1/stage-workflows/records/{workflowRecordId}` | `GET /api/v1/stage-workflow-records/{workflowRecordId}` |
| `PUT /api/v1/stage-workflows/records/{workflowRecordId}/advance` | `PUT /api/v1/stage-workflow-records/{workflowRecordId}/advance` |
| `PUT /api/v1/stage-workflows/records/{workflowRecordId}/outcome` | `PUT /api/v1/stage-workflow-records/{workflowRecordId}/outcome` |
| `GET /api/v1/stage-workflows/records/{workflowRecordId}/comments` | `GET /api/v1/stage-workflow-records/{workflowRecordId}/comments` |
| `POST /api/v1/stage-workflows/records/{workflowRecordId}/comments` | `POST /api/v1/stage-workflow-records/{workflowRecordId}/comments` |
| `DELETE /api/v1/stage-workflows/records/{workflowRecordId}/comments/{commentId}` | `DELETE /api/v1/stage-workflow-records/{workflowRecordId}/comments/{commentId}` |

No other route in the API is deprecated.

## Error response

Errors have a stable public shape:

```json
{
  "errCode": "DE0311000101",
  "errMsg": "Invalid request"
}
```

| HTTP status | Meaning |
| --- | --- |
| `400` | Invalid syntax, validation failure, or unsupported status transition |
| `401` | Authentication is missing or invalid |
| `403` | The authenticated caller lacks the required authority |
| `404` | The tenant-scoped resource does not exist |
| `409` | A unique value or aggregate version conflicts |
| `429` | A bounded public/connector admission limit or governed enterprise-data adapter quota is exhausted |
| `422` | Another stable domain rule rejected the operation |
| `503` | The enterprise-data adapter or its governed binding is unavailable |
| `500` | Unexpected internal failure; internal details are not exposed |
| `503` | Connector authentication storage is temporarily unavailable |
