# Development workflow

## Finance-list batch operations

Selecting current-page records can expose permitted batch actions: receivables support owner transfer and deletion; invoices support owner transfer, issue-result reset, and deletion. Every command carries the just-observed version for each selected record and validates the entire batch in one transaction. A stale version, hidden record, invalid state, or missing authority leaves every selected record unchanged. Invoice reset accepts only issued records; invoice delete accepts only pending records.

## Lead and contact batch operations

Lead and contact lists follow the same current-page selection flow. Permitted users can transfer the selected records to one currently active, in-scope member or delete the selection. Each request carries an observed version for every record; the service locks and validates the complete selection before changing an owner, withdrawing attachments, or soft-deleting anything. A hidden record, stale version, active-child dependency, or missing permission preserves the full selection unchanged.

## Opportunity batch operations

Opportunity lists expose the same current-page transfer and deletion flow. The replacement owner must be an active member within the caller's resolved assignment scope, while the server validates every selected visible opportunity's version and owner field policy before the first owner change. Deletion preflights the complete selection before withdrawing attachments or governed FILE values. Any failed record rolls back the whole command.

Use this guide when adding application behavior to atom-crm. The dependency and security rules in [architecture](architecture.md) still apply.

## Local development loop

Start MySQL:

```bash
docker compose up -d mysql
```

Build the project, then run the `start` module with the `dev` profile:

```bash
sh ./mvnw clean install

sh ./mvnw -f start/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev
```

Start the matching `atom-crm-web` checkout using its README. On a fresh database, open its `/setup` page to create the first administrator, then use `/login` for ordinary password sessions. Its development server proxies `/api` to the backend, preserving the browser session and CSRF flow. There are no default administrator credentials.

For explicit local integration tests only, the backend can be restarted with `ATOM_SECURITY_TRUSTED_HEADER_ENABLED=true`; the frontend separately requires `VITE_ENABLE_DEV_AUTH=true` to expose `/dev/session`. Select a member and tenant that actually exist in the local database. Do not assume actor ID `1`, fabricate membership, or use these switches in production. Trusted headers identify a local caller; they do not bypass current tenant membership, role grants, data scope, or field policy. See [configuration](configuration.md) for the complete boundary.

Stop local services without deleting their data:

```bash
docker compose down
```

## Add a use case

Work from the domain toward the adapters:

1. Add the domain behavior, value objects, policy, and domain error.
2. Add the use-case/error-scene identifier to `application/operation/UseCaseOperation`.
3. Add an application service method that accepts `AuthenticatedCaller`, calls `CallerGuard.requireTenant(...)`, and never derives tenant identity from request data.
4. Use `CommandServiceTemplate` for state changes and `QueryServiceTemplate` for reads; the latter supplies one read-only `REPEATABLE_READ` snapshot for multi-query hydration. Call domain behavior from the application service, not a controller or repository.
5. Define only repository, security, or output-port methods consumed by the use case, then implement the adapter in the appropriate `infra` module.
6. Register framework-neutral factories, policies, and domain services in `application/config/DomainConfiguration` when they need runtime dependencies.
7. Add or update the public Request, Response, and facade contract in `api`.
8. Bind the HTTP route and its authority in `infra/rest/SecurityConfig`, then repeat the same capability check through `CallerGuard` in the use case.
9. Register a new capability in the domain `AuthorityCatalog` with its intended tenant-assignable boundary. Exercise it with current database-backed role grants and membership; when the new use case handles scoped records or fields, add the relevant scope and field-policy checks. Updating trusted-header YAML alone does not establish tenant authorization.
10. Add domain, application, persistence, REST, and integration tests as applicable. `ArchitectureBoundaryTest` must still pass.

Commands use the `validate -> prepare -> execute -> onSuccess` lifecycle. `validate` and `prepare` run before `CommandServiceTemplate` opens an independent transaction for `execute` and `onSuccess`, so CPU-heavy preparation does not occupy a database connection. Register event publication and cache work as separate `AfterCommitExecutor` actions. Use a transactional outbox when delivery must survive process failure after commit.

Queries also validate the caller and tenant before a repository or cache lookup. Every cache key must include tenant identity, and a cache hit must not bypass ownership checks.

### First additional aggregate: complete checklist

For an `Order` aggregate with one create command and one detail query, inspect or add each applicable item below. The User implementation provides a nearby reference and remains part of installation, authentication and tenant-scoped user management:

| Area | Required work |
| --- | --- |
| `domain` | `Order`, `OrderId`, status/value objects, events/errors, `OrderRepository`, creation factory or method, and domain tests |
| `application` | `OrderService`, implementation, `OrderVO`, `OrderAssembler`, `UseCaseOperation` codes, `CallerGuard` checks, output ports, and application tests |
| domain wiring | Beans in `DomainConfiguration` for factories, policies, or services with constructor dependencies |
| `api` | Create/query Request types, `OrderResponse`, and `OrderFacade` contract |
| `infra/persistence` | `OrderPO`, `OrderPOConverter`, mapper, SQL/result map where names are exceptional, repository adapter, Flyway migration, and round-trip tests |
| `infra/facade` | Facade implementation using `ResultUtil.map(...)` for VO-to-Response mapping |
| `infra/rest` | Controller, caller mapping, route authority, validation, and HTTP tests |
| authorization | Register concrete operation capabilities in `AuthorityCatalog`; test current membership, role grants, data scope, and field policy |
| `start` | Integration tests and any runtime adapter needed by the new output ports |
| documentation | Public API/configuration changes in docs and `llms.txt` |

Do not pre-populate speculative repository searches, locks, specifications, or adapters. Add a port when the first use case consumes it, and implement filtering in SQL rather than loading a tenant into memory.

### Expose an aggregate field update end to end

For a command such as “change user email,” make one coherent vertical change:

1. Add a validated `UserEmailUpdateRequest` and facade method in `api`.
2. Add `UserService.updateUserEmail(caller, userId, request)` and a `USER_EMAIL_UPDATE` operation code.
3. In `validate`, call `CallerGuard.requireTenant(caller, "users:write")`; in `prepare`, load the visible tenant-owned aggregate.
4. In transactional `execute`, call `user.changeEmail(...)` and save through `UserRepository`.
5. In `onSuccess`, pull events, register event publication, and independently register `userCacheService.invalidateUser(...)`.
6. Add the facade delegation, controller route, and the same `users:write` route rule in `SecurityConfig`.
7. Test the domain transition, caller/tenant rejection, persistence round trip, cache invalidation, HTTP contract, and optimistic-lock conflict.

This path does not require a new repository method: update the loaded aggregate and persist it through `save`.

## Add a repository operation

Define the port in `domain` and require the tenant explicitly:

```java
Optional<User> findByEmail(TenantId tenantId, String email);
```

Implement it in `infra/persistence` with a tenant predicate:

```java
LambdaQueryWrapper<UserPO> query = new LambdaQueryWrapper<>();
query.eq(UserPO::getTenantId, tenantId.getValue())
     .eq(UserPO::getEmail, email);
```

A missing tenant must fail closed. Do not interpret a null tenant as an unscoped or administrator query. Cross-tenant administration needs a separate port and authority.

Repository ports describe current use-case needs. Do not add bulk, time-window, specification, or lock methods in anticipation of future requirements.

For aggregate updates, leave the loaded `version` unchanged until MyBatis-Plus performs the compare-and-increment. Treat zero updated rows as an `AggregateVersionConflictException`.

### Organization and target-tenant operations

Keep the current execution tenant and a target tenant separate. A department parent, department member query, and membership review must all use the caller-derived `TenantId` and an active `OrganizationMembership`. A target tenant is permitted only for membership-request submission and for data-only membership validation; neither path may update `AuthenticatedCaller`, a security context, or a token. Resolve department ancestors with the same tenant predicate and reject a move that reaches the department itself or repeats an ancestor.

Use `OrganizationTenantRepository.lockForStructureChanges(tenantId)` before every department structure mutation and every write that creates a department dependency, such as membership approval or target-tenant membership submission. The lock is the initialized tenant row's `FOR UPDATE` serialization point; obtain it before every target-tenant database read. Deactivation may retain inactive dependencies but must reject active children or memberships. Deletion must reject every child and membership assignment, so no persisted relation can point at a deleted department. Keep the `PENDING` membership-request uniqueness rule in a new Flyway migration with a database-enforced conditional key; a service-level existence check is not a concurrency guarantee.

Use bounded `PageResult`/`Pager` queries for tenant-wide member search, department members, and membership-request history. A department-member query first verifies that the department is visible, then uses one tenant-and-department-scoped join rather than per-member profile lookups. Batch department lookup accepts at most 200 IDs, and descendant queries must retain their explicit tenant predicate. Reference regions are separate read-only data: depend on `RegionReferenceCatalog`, not organization repositories or tenant membership tables.

### Customization metadata and field access

Use `BusinessObjectType` as the stable boundary for shared customization. `FieldDefinition` owns metadata only; a business aggregate owns its values and enforces its own uniqueness and transaction rules. Do not add table names, mapper identifiers, class names, or raw query fragments to field or saved-view requests.

Before a business read exposes field values, resolve the member's policy through `EffectiveFieldAccessResolver`: `HIDDEN` omits the value, `READ` applies its mask, and `WRITE` is unmasked. Before a write, require both the business use-case authority and effective `WRITE` for each supplied field. Field policy narrows an already-authorized use case and must never add an authority. An inactive member or an absent non-owner policy fails closed.

Keep field layouts and saved views personal by deriving `MemberId` from `AuthenticatedCaller.actorId()`. Validate layouts against the active field catalog and intersect them with that catalog again when reading. Reject field lifecycle changes while dependent layouts or saved views exist. Field-definition commands, layout writes, and saved-view writes must call `CustomizationObjectLock.acquire(tenantId, objectType)` inside the command transaction and before their first catalog or dependency read. The MySQL adapter uses one `INSERT ... ON DUPLICATE KEY UPDATE` statement to lazily create or directly acquire the exclusive lock on the shared `(tenant, objectType)` guard row. Do not split acquisition into an ignored duplicate insert followed by `SELECT ... FOR UPDATE`, which introduces a shared-lock upgrade, or replace the database boundary with a service-level pre-check or in-process mutex. For ID-only saved-view updates, resolve the immutable object type during validation, then acquire the guard and reload the view inside the transaction before checking its version or fields.

Validate saved-view operators against active searchable field types on writes and again before returning or executing them, then let each future business repository translate only the validated structure into parameterized, tenant-scoped predicates. Never execute or persist caller-supplied SQL, scripts, column names, or arbitrary JSON expressions.

Treat embedded pages as client link metadata. Accept only validated HTTPS templates, substitute the numeric `{recordId}` token, and return the result without performing DNS resolution or a network request. A server-side integration requires a separate output port, destination allowlist, timeouts, and SSRF controls.

### Customer aggregate and scoped queries

Use `CustomerAccessResolver` independently for every customer capability. Pass its explicit `CustomerVisibility` to the repository instead of loading a tenant and filtering in Java. Owner, department, subtree, tenant, and collaborator predicates belong in SQL; write visibility must distinguish a `WRITE` collaborator from a read-only collaborator. Delete and assignment use owner-scope visibility so collaboration cannot silently widen destructive operations. Treat out-of-scope and cross-tenant IDs as not found.

Resolve effective field access after record authorization. Require `WRITE` for every supplied built-in or custom value, preserve omitted patch values, and apply masking only while mapping the aggregate to `CustomerVO`. A saved view or duplicate check may use only active searchable fields with unmasked read access. Translate those already-validated conditions through a fixed built-in column map or parameterized custom-value predicate; never interpolate a client field code into SQL.

Persist the customer root and its bounded address, tag, collaborator, and custom-value children in one `CommandServiceTemplate` transaction. Acquire the customization object lock before the first customer or field-catalog read for every operation that can call aggregate `save`, even when the requested change only targets tags, state, ownership, hierarchy, or collaborators, because save synchronizes all child tables. Update the root first with tenant, ID, and version predicates, then synchronize the same aggregate instance so optimistic conflicts roll back every child-table mutation. Persist each tag's display name separately from its NFKC/lowercase `tag_key`, reuse the domain key for every tag operation, and group the visible tag catalog by that key. Custom unique values need a normalized database reservation key; a pre-check can improve feedback but is not the concurrency boundary, and a field's unique flag is immutable after creation. Soft deletion retains aggregate data, changes only status, and releases active unique reservations.

Acquire `CustomerStructureLock` before the first hierarchy read or write. Check self-parenting and descendants under that lock so two opposing moves serialize, and reject an attachment when the resulting complete tree would exceed 64 levels or 2,000 nodes. This write invariant makes the recursive query budget complete for every API-created tree, so hidden nodes cannot consume the budget and arbitrarily displace visible descendants. Apply `CustomerVisibility` before enforcing the public result limit. Acquire the customization object lock before reading field definitions for a customer write; this prevents a field lifecycle change from racing a value write that validated stale metadata. Mutation responses must resolve parent visibility explicitly rather than using an empty relation set, and only assignment-authorized command paths may include collaborators in the common customer response.

Database coordinate constraints must express the address pair invariant without relying on SQL `UNKNOWN`: allow both longitude and latitude to be null, or require both to be non-null and in range. Correct an already-versioned constraint with a new Flyway migration and cover both one-sided coordinate inserts through native SQL.

### Customer pools and ownership transitions

Keep pool configuration in the `CustomerPool` aggregate and current membership in a tenant-scoped relation keyed by customer. Do not encode administrators, eligibility, or rules as comma-separated values or untyped JSON. Pool entry, claim, assignment, transfer, and deletion are multi-row commands: acquire the CUSTOMER customization guard first, then pool rows in numeric order where more than one pool is involved, then membership rows. Change and save the same `Customer` instance before updating membership/history so any optimistic conflict rolls back the whole transition.

Resolve pool capability, active organization membership, pool eligibility/administration, and customer field access as separate restrictions. Releasing also resolves `customers:write` and its data scope; a pool capability must never widen ordinary active-customer access. Apply member, department, and administrator catalog eligibility in the database before returning results, while treating the persisted tenant owner as tenant-wide. Use the dedicated catalog projection for root fields and the current-member administrator marker; do not hydrate aggregate configuration relations. Batch aggregate customer counts for every returned pool. Pool reads use membership-scoped repository predicates and the existing CUSTOMER field resolver and saved-view policy; resolve parent visibility against the same pool before mapping a relationship. Do not return management configuration from the member catalog.

Daily claim limits and previous-owner cooldowns use persisted claim/membership state under the pool row lock. Redis counters and in-process mutexes are not correctness boundaries. Persist an automatic-release cursor per tenant and advance it in the same transaction as the customer changes. Resume after that pool ID, wrap once, and bound both page size and distinct pools locked per invocation so repeated calls rotate fairly without holding the CUSTOMER guard across every pool. A scheduler adapter must not synthesize identity or accept a tenant from an untrusted job payload.

### Contacts and contact pools

Use the same aggregate, lock, field-policy, and row-lock patterns for `Contact` and `ContactPool`, but never share customer persistence relations or capability codes. `job_title` and `next_contact_time` are built-in, searchable contact fields and must traverse the API, aggregate, persistence, and field catalog together. Validate a submitted contact customer ID with the caller's independent `customers:read` visibility before saving it; resolve that visibility again when mapping contact output, so a later role or scope change suppresses the link. Treat omitted patch properties as unchanged, explicit `customerId: null` as unlinking, and explicit `nextContactTime: null` as clearing the time. Saved-view `customer` and `parent_contact` conditions must be restricted to visible `EQ`/`IN` targets; reject other relation operators so saved views cannot act as a relationship-visibility oracle. Contact-pool release must resolve `contacts:write`, and contact-pool rows, membership rows, and history must be locked and updated independently from customer pools.

### Products and opportunity product snapshots

Treat the product catalog as tenant-shared reference data. Require active membership plus the relevant `products:*` capability, but do not create a data-scope predicate from product ownership. Acquire the PRODUCT customization guard before loading field definitions for a product write, then acquire `ProductCatalogLock` before reading or updating product/category state. On create, require field `WRITE` for name, code, and list price, and require it for category, unit, description, owner, or availability only when the request actually supplies that optional field. Category tree checks must run under the same tenant lock, keep the complete active tree within 16 levels and 5,000 nodes, and reject deletion while a child or non-deleted product depends on the node. Keep NFKC/lowercase product code uniqueness and custom-field unique reservations in database constraints; application prechecks are only diagnostics.

For product CSV export, require both `products:read` and `products:export` before opening the bounded read snapshot. Re-resolve current PRODUCT field access, reject masked, hidden, relation, FILE, inactive, duplicate, or unsupported requested field codes, and apply the same keyword/category/owner/availability/sort/saved-view or temporary-condition validation as the product query. Do not let the request choose tenant, page, separator, locale, filename, SQL, or output destination. Generate at most 5,000 rows, 50 selected fields, and 16 MiB in memory before the HTTP body; use UTF-8 BOM RFC 4180 CRLF output, formula hardening and a one-shot releasing stream. The export remains a response-only operation: never create an attachment, import draft, history record, job, receipt, or download token.

Store opportunity products as aggregate-owned immutable snapshot lines. A replacement must reapply opportunity write visibility, require unmasked `WRITE` access to `OpportunityFieldCatalog.PRODUCTS`, require `products:read`, and require unmasked PRODUCT reads for name, code, category, unit, and list price. Acquire `ProductCatalogLock` before the command transaction's first consistent read, then lock distinct product IDs in numeric order and reject any missing, cross-tenant, deleted, or unavailable target; this keeps a concurrent product move and category lookup in one catalog state under MySQL repeatable read. Do not accept name, code, category, list price, discount, line amount, tenant, or owner from the request. Snapshot display data from the locked catalog rows, calculate discount and line amount in the domain, preserve request order, cap the collection at 100, and save it with the same opportunity instance so optimistic locking protects all lines atomically.

### Contracts, invoices, receivables, and invoice profiles

Treat contracts and invoice applications as separate tenant-owned aggregates with independent `contracts:*` and `invoices:*` capabilities and data-scope resolution. Validate every target owner against the resolved assignment owner scope, not merely active tenant membership. Validate customer, opportunity, contact, contract, and product relationships through their owning domain's current read predicate; never let a relationship transfer visibility. Reapply those predicates on mutation responses as well as queries, intersect collaborator output with the per-record assignment owner scope, and omit collaborators from ordinary detail replacement responses. Back contract opportunity/contact customer equality with composite foreign keys so later relation changes fail closed. Build non-empty contract product rows from locked available products after checking the five unmasked PRODUCT snapshot fields. The dedicated contract product relation endpoint requires both contract/product visibility and those unmasked fields even for an empty replacement, returns only its complete versioned line baseline, and locks the product catalog before replacing it. Accept only product ID, quantity, and optional unit price, then calculate discount, line subtotal, contract subtotal, and total in the domain. Recheck the NFKC-normalized contract-number key length, aggregate `DECIMAL(22,2)` precision, and MySQL DATE bounds rather than relying on column failures.

For contract list bulk actions, submit only `ContractBatchOwnerUpdateRequest`, `ContractBatchDeleteRequest`, `ContractBatchCollaboratorAddRequest`, or `ContractBatchCollaboratorRemoveRequest`; never fan out from a browser to per-record mutation APIs. Every request is bounded to 100 unique observed-version targets and executes under the contract customization lock. Preflight the complete sorted target set before the first mutation. A transfer validates the selected active member and owner-field policy; a delete validates invoice, receivable, and plan dependencies before it withdraws attachments or persists any deletion; collaborator add verifies active members and merges only the named collaborators with `READ`/`WRITE` plus nullable inclusive `effectiveUntil`, while remove rejects an owner and leaves unrelated collaborators intact. Reject a past deadline; a null deadline means unlimited, and a passed deadline no longer grants collaborator-derived read/write visibility. Keep every validation and write in one command transaction so conflicts and business errors cannot leave a partial selection changed. For selected CSV export, use `ContractBatchCsvExportRequest` rather than a query or caller-selected column list: it permits 1–100 unique observed-version list rows, rechecks both `contracts:read` and `contracts:export` scopes, and writes only fixed readable/unmasked contract columns through the bounded formula-safe CSV encoder. Return it as a controlled one-shot `no-store` response; never serialize relationships, custom fields, raw request data, or an arbitrary export destination.

Use the contract root row as the serialization point for the invoice ceiling. Invoice create/update must lock the visible active contract before summing other non-deleted invoices; contract update must take the same lock before allowing a customer or total change. Reject customer replacement once any invoice exists, reject a reduced total below the committed invoice sum, and keep a tenant/contract/customer composite foreign key as the persistence backstop. Translate named relationship/customer foreign-key races on contract save into a stable domain error. Deriving an invoice customer from a visible contract does not make that customer visible in the response; independently reapply `customers:read`, and independently reapply `contracts:read` when projecting issue, reset, or owner-change responses. Invoice profiles use a status enum and every repository call fails when tenant is absent. Reapply current customer visibility inside the profile read or write transaction; list and create must check before their profile query or insert, and update/delete must check before mutation. Cap active profiles at 100 per customer, serialize visibility-plus-count-plus-insert through the INVOICE customization guard, hard-limit repository list queries, and clear organization-only fields when the title type becomes personal. Normalize blank contract or invoice keywords to no search before requiring search-field readability.

Receivables and plans share the contract row as their monetary serialization point. Derive both customer IDs from that locked visible contract, retain composite tenant/contract/customer foreign keys, and recheck customer, contract, and plan relationship visibility before output. Receipt create and amount replacement must sum every other active receipt while holding the contract lock, then reject totals above the contract or selected plan; plan writes must reject planned totals above the contract. Do not persist received or progress counters: derive them from active receipts. Keep a plan immutable once any receipt history exists, including a soft-deleted receipt. The available-plan endpoint must require unmasked plan amount access and omit plans whose active receipt total has reached their planned amount. Contract decrease checks must include active received totals, and contract deletion must reject any receipt or plan history. Add a new Flyway migration and bootstrap provisioner for both field catalogs, then extend approval facts and record visibility only for the receipt root; approval lifecycle behavior remains an explicit separate use case.

### Activities and return visits

Model an activity as a separate timeline aggregate with a typed subject, not as a generic request map or an unscoped child of one CRM table. Before storing an activity, call `BusinessRecordAccessGuard` for the subject in write mode and every related record in read mode; before reading, call it again for the subject. Keep user follow-ups mutable only by their author and make system-generated entries append-only. Always page activities by one visible subject so the database count cannot disclose hidden records.

For a return visit, use the independent `ReturnVisitAccessResolver` and acquire the `RETURN_VISIT` customization guard before loading fields or mutating the root. Validate the mandatory customer and optional contact/contract through their own current read scopes, then verify the optional records have the same persisted customer before calling aggregate behavior. Persist typed custom values and unique reservations together with the root. Recheck each relationship when constructing the response; suppress it when the current caller no longer has access. Add built-in catalog rows both to the Flyway backfill and the tenant bootstrap provisioner.

Treat activity audit entries as an internal append-only use case: record only a server-defined summary after a successful controlled activity or return-visit write, and never expose a request field for actor, tenant, IP address, raw request data, or audit content. Follow-up methods and templates belong to a normalized tenant catalog; validate activity input against it inside the command boundary.

A worklist must be a fresh bounded projection, not a mutable copy of source records. Give each source an independent stable cursor, merge by due time/kind/source ID, and advance a source only through records consumed by the page. Recheck CRM/OA subjects with `BusinessRecordAccessGuard` and require unmasked due/summary fields. For a Work task, use its dedicated repository projection instead: require both worklist and task-read capabilities, select only its ID, projection version, due time, and title, then apply tenant, current assignee, active membership, open status, due range, and per-source cursor in SQL without consulting its optional CRM link. Store acknowledgement separately by tenant, member, kind, source ID, source version, and due time so later source versions or dates reappear. Fail a category acknowledgement without writing any rows when its bounded verification scan cannot reach the final page. When adding a source, version the opaque cursor and explicitly decode the previous version; a missing new-source position should be exhausted for an old continuation rather than started mid-chain. Do not call external calendar, OA, or scheduling systems from this boundary.

A Work task is a separate tenant aggregate, not a generic table shared with approval or stage tasks. Keep exactly one current assignee in the root, make creator/assignee/follower visibility an SQL predicate before count and pagination, and treat `work-tasks:manage` as an explicit modifier that implies neither read nor write. Creation, reassignment, and follower replacement acquire the organization tenant guard before the task root and then re-read active membership, so member suspension cannot race a new participant into validity. Cap followers at 50 unique active tenant members; followers may read and comment but must still fail assignee-only progress and creator/manager structure checks.

Keep comments outside the task aggregate as tenant/task-scoped, independently versioned rows. Page only active comments; soft deletion must preserve the row so any historical comment continues to block draft-task deletion. Comment creation and deletion lock the task root before the comment row, reapply current task visibility, and allow deletion only to the current active author. Comment text is trimmed plain text with a 5,000-code-point bound; never render it as HTML or copy it into an Activity.

Store at most one checked CRM relationship on the task root. Accept only CUSTOMER, CONTACT, LEAD, OPPORTUNITY, CONTRACT, INVOICE, RECEIVABLE, or RECEIVABLE_PLAN. On create or a non-null replacement, call `BusinessRecordAccessGuard` in write mode for the explicit new target after locking the task and require the returned tenant to match the verified Work tenant. Do not resolve the previous target when clearing or overwriting it: this keeps a hidden relationship and no relationship observationally equivalent and lets stale or deleted links be detached. For details, mutation responses, and pages, batch relation IDs per type under the query snapshot and independently reapply read scope; omit an unreadable relation without hiding the visible task. A relationship-filtered page must validate current record readability inside the same `REPEATABLE_READ` transaction that applies relationship and task-participation predicates before SQL count/page slicing. Never write the task ID into the CRM aggregate, create an Activity, or infer task access from CRM access.

Persist lifecycle, first-start, terminal, audit, optimistic, and worklist-projection versions as server facts. Only title, priority, assignee, due time, and status advance the projection version; descriptions, followers, comments, and CRM relationships do not. Keep due time nullable and calculate overdue on read rather than persisting a drifting state.

When adding a new built-in field catalog, update both the Flyway backfill for existing tenants and the organization bootstrap provisioner for future tenants. When a new business object participates in approvals, add authoritative fact resolution, single-record visibility, bounded batch visibility, and independent output visibility for every relationship fact together.

### Marketing-form submission history

Keep successful public submission attribution outside the `MarketingForm` aggregate as an immutable, tenant-scoped relation. Append it only after `LeadCreationCoordinator` returns a persisted lead and while the same outer command transaction still owns the locked form row; do not invoke another command template or an after-commit callback for this write. Assign the timestamp in the insert with MySQL `UTC_TIMESTAMP(6)`, reload the persisted row, and store only the internal form ID, actual locked form version, lead ID, and database UTC time. Do not add submitted field values, IP addresses, headers, referrers, cookies, UTM parameters, failed attempts, or exception data to this ledger.

Treat the accepted-count trend and visible-lead page as different authorization contracts. A trend proves active membership, `marketing-forms:read`, and a tenant-owned form, then aggregates only ledger rows and fills bounded DAY/WEEK/MONTH buckets without loading leads. A visible-link query additionally resolves the querying caller's current `leads:read` visibility. Put the lead status, owner-department, data-scope, and collaborator predicates inside the SQL used by both count and page slicing; never page ledger rows first and filter loaded leads afterward. Preserve ledger rows when a form or lead is soft-deleted, allowing stable historical counts while current hidden/deleted leads disappear from link pages.

### Approval and stage workflow changes

Keep routing configuration in `ApprovalDefinition` and runtime decisions in `ApprovalInstance`; never re-read a changed definition to rebuild a running route. New routing inputs must be active searchable field codes normalized by `ApprovalFactPolicy`. Public preview and submission must build condition facts from the owning persisted aggregate, never from caller values. A pending-data use case must first enforce business validation and customization `WRITE` access, then pass an immutable snapshot; merge omitted condition fields from the authoritative record. Extend both preview and submission through `ApprovalSubmissionCoordinator` so they cannot disagree. Snapshot managers with the instance and use that set for historical read/cancel checks. Any new actor source must resolve to at most `ApprovalStepDefinition.MAX_ACTORS` unique active tenant memberships and must not accept caller-supplied roles, tenant IDs, SQL, scripts, or table names.

Approval actions load the root with `FOR UPDATE`, reapply the owning business record's current read visibility, verify the request version, and mutate the same aggregate instance before saving its task children. Apply the same visibility rule to batch actions, withdrawal, cancellation, bucket queries, and pending counts; a participant relationship never overrides a later business-scope change. Before returning approval or stage facts, resolve the caller's current `EffectiveFieldAccess`, omit `HIDDEN` codes, and apply the configured mask. Relationship facts need an additional owning-domain check: batch IDs per CUSTOMER, CONTACT, OPPORTUNITY, or CONTRACT domain, omit facts outside its current read scope, and fail closed when the caller lacks that capability. Apply this consistently to details, mutation responses, batch summaries, and pages. Preserve transfer history by appending a replacement task. Multi-instance commands must validate at most 50 unique IDs, sort before locking, and run under one `CommandServiceTemplate` transaction. Do not catch failures to return partial batch results. Approval bucket pages must fetch root-only summaries after visibility slicing, and batch actions must also return summaries; the single-instance detail contract remains the canonical complete task, manager, and watcher timeline. When a generic approval bucket spans object types, scan bounded repository chunks, resolve each owning domain's visibility in bounded ID batches, slice only the visible sequence, and count every visible match so hidden records cannot consume pages or corrupt totals. Normalize a public idempotency key once and reuse that trimmed value for reserved-prefix validation, repository lookup, and aggregate persistence. A role route is eligible only while the role itself is active; retained assignments on an inactive role must not resolve approvers. Reject normalized duplicate applicant-selected step keys and null container elements at application/domain boundaries so direct callers still receive `PARAMETER_INVALID` instead of an unknown failure.

Stage records store stable object type/record ID references only. Every endpoint must call `BusinessRecordAccessGuard` with the correct read/write mode before exposing a record, comment, or approval gate. To start a record, use the preflight definition only to identify the object lock, then reload and revalidate the workflow, current business visibility, applicability, and active approval references while holding that lock. Approval-definition status changes, active workflow updates, activation, and runtime start share the object lock; this serializes reference checks so active workflows cannot race with approval deactivation. Stage-required fields must stay active and searchable, and every required field must normalize to at least one value; field lifecycle changes must reject existing workflow references. Validate the union of required fields and referenced approval-condition fields against `ApprovalInstance.MAX_ROUTING_FACTS` both from workflow mutations and approval-definition mutations. Generate stage gate titles through the bounded title function rather than concatenating unbounded configuration names. Stage configuration becomes immutable after a record references it. Approval gates use a deterministic key in the reserved `stage:` namespace, public submissions must reject that prefix, and an explicit later advance consumes the terminal approval result. Approval transactions do not mutate business aggregates.

For OA request runtime, keep the public form-bound command separate from general approval submission. Lock and reload tenant membership, form, fields, and approval configuration in the outer command transaction; derive identity facts from the verified caller and current membership; persist normalized request facts before resolving the approval through `ApprovalSubmissionCoordinator`; and use `BusinessRecordFactResolver` to read the stored request. Do not call `ApprovalService` while holding OA locks. Link the `oa:<requestId>` instance before commit and keep the request immutable afterwards. Apply applicant/task/manager/watcher visibility in repository SQL before pagination, then reuse the same `OaRequestAccessResolver` from `BusinessRecordAccessGuard`. Keep worklist on its existing approval source and do not introduce any Work dependency.

For OA announcements, keep editable selectors on the draft aggregate and recipient membership in a separate immutable snapshot. Publication must acquire the organization tenant lock before the announcement root, recheck the manager and selector targets, resolve only active same-tenant memberships, fetch one row beyond the 10,000-recipient limit, and abort instead of truncating. Persist the published root and chunked snapshot in the same command transaction. Inbox predicates must combine tenant, `PUBLISHED`, recipient snapshot, and current active membership before count or page slicing. Management page/detail reads must recheck active membership as the first read in the same query snapshot as their statistics. A read command must shared-lock the membership before the visible announcement root, then use the receipt unique key as an insert-if-absent boundary so suspension, withdrawal, and concurrent retries have a deterministic order without exclusively locking a popular root. Never render the body, enumerate recipient profiles in statistics, reuse worklist acknowledgements, or emit best-effort events as if they were reliable notification.

## Add a database migration

Create the next migration in:

```text
infra/persistence/src/main/resources/db/migration
```

Use a versioned name such as:

```text
V2__add_user_last_login_time.sql
```

Update the migration, affected PO types, converter mappings, mapper XML, aggregate reconstruction, and tests in the same change. Add a column to an aggregate and `reconstitute` only when it is domain state. JSON columns may store bounded validated metadata such as field options or personal layouts; converters must expose typed domain values instead of raw JSON. A non-transactional Java backfill must detect every completed schema phase and safely resume after `Flyway.repair`; replace keys supporting foreign keys in one atomic `ALTER TABLE`, and test both the preceding-version upgrade and a failed-backfill retry. Freeze historical transformations in the migration itself and give Java migrations a stable checksum; never delegate them to mutable domain policies.

Flyway is the only schema source. Do not add `schema.sql`, Docker init SQL, or a test-only schema.

## Add an HTTP endpoint

Keep transport work in `infra/rest`:

1. Bind and validate Request data.
2. Receive Spring Security `Authentication` and map it with `AuthenticatedCallerMapper`.
3. Call the facade.
4. Map the result to an HTTP response.
5. Add route authorization in `SecurityConfig` and repeat the capability check in the application use case.

Do not accept actor ID, tenant ID, authorities, or administrator flags from an ordinary request body. Do not log passwords, tokens, or complete request objects that may contain them.

Use stable public errors and the narrowest internal failure type. Unexpected exceptions are logged internally and mapped to a generic response; stack traces, SQL, credentials, class names, and arbitrary exception messages are never returned.

## Verify the change

Select tests for the affected behavior. For cross-module changes, follow focused tests with the explicit fast suite; for database, security, transaction, mapper, cache or event changes, also run the default suite with Docker available:

```bash
sh ./mvnw test -Datom.test.skip-database-tests=true
sh ./mvnw test
```

The default suite skips database tests when Docker is unavailable, regardless of `CI=true/false`. Check that the relevant MySQL tests actually ran before claiming integration coverage. See [testing guide](test-guide.md) for test selection and documentation-only checks.
