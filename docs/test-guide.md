# Testing guide

Test domain behavior without Spring, application orchestration through ports, adapters at their boundaries, and the assembled application against MySQL.

## Commands

Run the explicitly fast suite without database tests:

```bash
sh ./mvnw test -Datom.test.skip-database-tests=true
```

Run one module and its dependencies:

```bash
sh ./mvnw -pl domain -am test
sh ./mvnw -pl shared -am test
sh ./mvnw -pl application -am test
sh ./mvnw -pl infra/persistence -am test
sh ./mvnw -pl infra/rest -am test
```

Run the default suite, including MySQL integration tests when Docker is usable:

```bash
sh ./mvnw test
```

`DatabaseTestCondition` checks Docker availability unless `atom.test.skip-database-tests=true` explicitly opts out. Without Docker, database tests are skipped; `CI=true/false` does not change this gate. Testcontainers starts MySQL 9.7.1 LTS; Redis remains disabled. For changes requiring database evidence, ensure Docker is usable and inspect the relevant Surefire execution/skip counts; Maven success alone does not prove that those tests ran.

The checked-in GitHub Actions workflow runs `sh scripts/release-verify.sh` (also exposed as `make release-verify`) for every pull request and every push to `main`; it can also be triggered manually. Release verification requires a working Docker daemon, checks release metadata, runs a clean Maven install, and inspects the resulting Surefire XML. Every database test class discovered from `@RequiresDatabase` or `BaseIntegrationTest` must have executed cases with no skips, errors or failures; missing reports also fail the release gate. Local `make test` and `make integration-test` retain the automatic Docker gate; use the explicit skip property above for a fast run.

The report verifier uses the existing JDK 25 source launcher and needs no additional runtime. Its fixture self-tests cover missing/empty reports, partial and complete skips, failures, parameterized/dynamic and nested cases, and malformed or unsafe XML:

```bash
java scripts/VerifyDatabaseTests.java --self-test
java scripts/VerifyDatabaseTests.java  # inspect the latest start/target/surefire-reports
```

Use the second command only after a complete test run; the release script's `clean install` prevents stale reports from supplying evidence. CI retains Surefire reports for 14 days, including failed runs. Review the relevant report when diagnosing a failure; workflow success requires the database gate to pass.

For one test class in a reactor run, add `-Dtest=<TestClass> -Dsurefire.failIfNoSpecifiedTests=false` so dependency modules without that class do not fail selection. Add regression coverage for changed behavior at the lowest useful layer. Documentation/instruction-only edits need link, command and diff checks rather than a Maven build. Once appropriate checks pass, repeat or broaden them only for a new change, failure or unresolved concern.

## Test layers

| Layer | Test focus | Typical dependencies |
| --- | --- | --- |
| Domain | Value validation, aggregate transitions, policies, events | JUnit and assertions only |
| Shared | Result mapping, safe public errors, and pagination metadata | JUnit Jupiter |
| Application | Authority checks, tenant propagation, orchestration, post-commit scheduling | Mocks or small port fakes |
| Persistence converter | Full PO/aggregate round trip, version, timestamps | MapStruct mapper instance |
| REST | Error/status mapping, authentication rejection, safe messages | Spring MVC and Security test support |
| Start integration | Flyway, MySQL, tenant isolation, locking, HTTP flow | Spring Boot, MockMvc, Testcontainers |
| Architecture | Forbidden dependencies, adapter bypasses, entity setters, naming | ArchUnit bytecode inspection and Maven Enforcer |

Use the lowest layer that proves the behavior.

## Representative tests

### Domain

Create aggregates through their public factories and invoke behavior directly:

```java
@Test
void delete_active_user_changes_status_and_records_event() {
    User user = createUserForTenant(1L);

    user.delete();

    assertTrue(user.isDeleted());
    assertFalse(user.getDomainEvents().isEmpty());
}
```

Reconstitution tests also verify that persisted `version` is restored without new domain events. Domain tests do not use Spring, databases, HTTP, or mocked domain objects.

### Shared

Keep shared tests free of Spring. Cover result mapping, fixed public messages for unexpected failures, error-context chaining, complete error-code formats, and pagination metadata separately from REST transport tests.

### Application

Test through output ports. For human use cases, verify that an invalid caller or missing authority is rejected before repository access and that the caller's `TenantId` reaches every repository and cache call. Connector security tests verify that human and connector subjects are not interchangeable, full credentials are one-time outputs, only verifiers persist, the exact credential and identity lookups keep separate minimal projections, registration resolution happens before business access, credential flood state is bounded, and revoke/disable/retire fail closed. The integration fixture additionally proves the immutable tenant/reference binding and that concurrent disable is ordered against the tenant-scoped registration lock. Every later connector ingress command must reuse that lock before business writes and carry only the resolved tenant/connector scope.

Connector tests must reject revoked or wrong-tenant registrations before business access, retain authority-free connector authentication, and fence stale inbox attempts. Durable adapter checks must prove journal-before-ack, dispatch-before-I/O, deduplication, reconnect gaps, ambiguous outcomes and independent oracle state. Sensitive-data and recording requirements remain prerequisites for any future production adapter; a transport probe cannot satisfy them. See [connector verification](connector-integration.md#verification).

Enterprise-data tests separate native mapping, tenant policy, current readiness, one-call transport limits, quota persistence and explicit Customer adoption. Verify identity mismatch and excluded-field rejection, ephemeral query output, one-time caller-bound previews, fresh Customer/field authorization, atomic identity reservation/provenance and bounded retention. Synthetic and live evidence are distinct. See [enterprise-data verification](enterprise-data.md#verification).

File tests cover exact immutable content, target-first current authorization, byte/inspection/publication separation, stale-worker fencing, crash points, bounded streams, range/cancel behavior and authoritative exact-generation deletion. Prove cross-tenant and cross-target isolation, native path/payload redaction, private-draft cleanup, field snapshots and public draft capability consumption. Model, MySQL and real Linux/scanner lanes provide separate evidence. See [file verification](file-storage.md#verification).

For scheduled automation, unit-test the closed handler catalog, disabled/invalid policy failure closure, absolute duration/concurrency bounds, same-occurrence recovery, result-code allowlist, safe-background logging (no exception class/message) and absence of any caller in a scheduled context. Docker/MySQL tests must prove schedule activation/pause generation, unique occurrence and active-slot arbitration under two claimers, lease takeover fencing, coalesced downtime, retry reuse of evaluation time, terminal retention, global V1→V2 revision reconciliation/cancellation and rejection of the stale replica, and object-guard → schedule/run → cursor ordering against concurrent pool configuration. Assert fixed-cardinality metrics for materialized/claimed/succeeded/retry/dead/cancelled, due lag, ledger state snapshots, lane active/queued and latest-success UTC. Run the Customer and Contact cases independently; a candidate or cursor from one kind must never affect the other. Test the single composition-root pulse with a deliberately slow attachment worker to show that the automation lane remains runnable, while each attachment invocation still calls only its existing `runOnce()` and retains its own job ledger.

For Work recurrence, unit-test DAILY/WEEKLY/MONTHLY first occurrence, interval anchoring, leap/month-end clamp, DST gap-forward and earlier-overlap offset, fixed-offset rejection, cap/end transitions, coalesced misfire, future-only generation, persistence-range exhaustion and propagation of rule/tzdb faults. MySQL tests must cover the V67 origin/occurrence identities, one generated open task per series, stale run fencing, current creator/assignee pause, follower omission, tenant/member constraints, persisted `next_due_at` projection, cursor membership in its current rule, series page visibility, and recurrence tzdb/policy control drift. The vertical `WorkTaskRecurrenceIntegrationTest` additionally proves API create/activate → materialize → task/occurrence/cursor/run-success atomicity with one database-UTC timestamp across task, series and occurrence, a rollback crash cut before run settlement, and competing lease-owner fencing. A mismatch may not create tasks or make activation available, but must not stop ordinary Work CRUD.

For Work reminders, unit tests cover the eight-step and timing bounds, plan/due-time invariant, MySQL `DATETIME(6)` derived due/deadline bounds, notification projection changes, and series-template future generation. `WorkTaskReminderIntegrationTest` runs V69/V70 against MySQL Testcontainers with `atom.scheduling.pulses-enabled=false` before its Spring context starts, then invokes the reminder pump explicitly. It proves a real reminder notification, per-recipient durable uniqueness, concurrent first-read idempotency without shared-to-exclusive lock upgrade, read-receipt preservation across a stale task projection, one-row expired-lease takeover, deadline transition to `DEAD`, and notification-before-occurrence terminal retention. MySQL coverage retains the task-root-before-occurrence lock order, stale-pending cancellation, current membership/participant/capability filtering before inbox count/page/read, and an overdue reminder that changes no Work state or worklist acknowledgement. Health tests must also prove that a failed purge, claim, or unrecoverable recovery produces `RUNTIME_FAILURE` until the next successful lane execution. No Work reminders test may introduce provider delivery or an external side effect.

For notification delivery, unit and MySQL tests must prove that an opt-in reminder effect atomically creates one `(tenant, notification, EMAIL)` outbox row only for a valid enabled dispatch deployment, while opt-out, disabled, or invalid deployments create none; deadline/retention maintenance must nevertheless reclaim legacy rows without exceeding notification-content retention. Readiness is preflighted before claim and rechecked in task-first admission: `UNSUPPORTED` and transient not-ready states leave rows unclaimed; current membership, `notifications:read`, `work-tasks:read`, participant visibility, current projection, preference, profile endpoint and readiness are all required before provider I/O. The crash oracle must run `IDEMPOTENT_SEND(window)` and `AT_MOST_ONCE_SEND` separately: only the exact original delivery ID/payload/HMAC endpoint binding/key version may recover inside the former window, and persisted ambiguous certainty survives deadline, retry exhaustion, stale-task handling and admission recovery. Exercise profile endpoint drift and a cancellation-aware adapter timeout: neither may send a changed command, dispatch budget is constrained by the remaining lease/deadline after settlement reserve, timeout must settle under the frozen guarantee and a lost settlement fails the lane. Verify real `SecurityFilterChain` authorization for both preference routes, `ACCEPTED` never changes inbox read/task/worklist state, tenant-scoped settlement never reads by global ID, terminal outcome metrics commit only with their owning transaction, and logs/metrics/ledger omit endpoint/provider values. Production remains disabled without a current real-adapter conformance snapshot.

`SafeFileComposedFixtureTest` checks the independent model; `AttachmentComposedConformanceTest` exercises runtime composition. Neither fixture alone establishes production storage/scanner readiness. Keep fixture expectations independent of implementation state and preserve stable report/scenario identifiers.

Runtime file conformance must retain unique/FK/lock/lease evidence, process termination, first-byte streaming cuts, one-file/one-parent MySQL proof and current storage/scanner build manifests. A model storage/scanner cannot declare its own expected `AVAILABLE` or `REMOVED`, and no synthetic pass creates production readiness.

For commands, verify that `validate` and `prepare` precede the independent transaction, and that a failure rolls back before it is converted to a `Result`. Post-commit work runs after commit and does not run after rollback; register event and cache callbacks independently. For queries, verify that validation/preparation remain outside an independent read-only `REPEATABLE_READ` transaction and that compound root/child hydration retains one snapshot across a concurrent commit. `CommandServiceTemplateTest`, `QueryServiceTemplateTest`, and `AfterCommitExecutorTest` are the references.

Customer CSV export application and MySQL tests must prove independent `customers:read` plus owner-derived `customers:export` SQL predicates, including collaborator-only denial, active/non-pool filtering, cross-tenant rejection, saved-view and keyword policy parity, field-policy fail closure, 5,001-row rejection, 200-ID batching, and one `REPEATABLE_READ` snapshot. CSV fixtures cover BOM, code headers, CRLF, RFC 4180 quoting, null/empty values, locale-independent scalar formatting, Unicode, and formula prefixes hidden by whitespace/control characters. REST tests assert that all use-case failures remain JSON before the body, success has the fixed download headers, no repository work occurs after document completion, and per-member/per-replica admission stays held through stream completion or cancellation.

Contact CSV export application and MySQL tests must prove independent `contacts:read` plus owner-derived `contacts:export` SQL predicates, including collaborator-only denial, active/non-pool filtering, cross-tenant rejection, Customer/parent Contact filter-target revalidation, saved-view and keyword policy parity, field-policy fail closure, 5,001-row rejection, 200-ID batching, and one `REPEATABLE_READ` snapshot. Contact and Customer exports share one per-member/per-replica admission budget but keep their typed documents, query projections, repository paths, and filenames separate.

Lead CSV export application and MySQL tests must prove independent `leads:read` plus owner-derived `leads:export` SQL predicates, including ordinary read versus export scope intersection, missing-authority rejection, Lead field-policy fail closure, non-projectable relation rejection, 5,001-row rejection, 200-ID bounded projection, formula-safe completed output, and a real MySQL download response. Customer and parent-Lead conditions must retain exact current-target visibility revalidation before candidate selection.

### Persistence

`UserPOConverterTest` covers every persisted User field in both directions, including tenant, password hash, flags, optimistic-lock version, audit timestamps, and reconstruction without events. `OrganizationPOConverterTest` covers the organization profile, membership, request, and root-department sentinel mapping. `CustomizationPOConverterTest` covers field options, member layouts, role field policies, typed saved-view conditions, embedded pages, versions, and audit timestamps. `CustomerPOConverterTest` covers the customer root and aggregate-owned address, tag, collaborator, and typed custom-value children without creating events. `CustomerPoolPOConverterTest` covers pool configuration, bounded relationship children, rules, version, and timestamps. `OpportunityPOConverterTest` covers the opportunity root, customer/contact relations, collaborator, outcome, expected values, custom values, product snapshot lines, version, and event-free reconstruction. `OpportunityPipelinePOConverterTest` covers pipeline and stage roots, their binary exact codes, lifecycle status, versions, audit fields, and event-free reconstruction. `ProductPOConverterTest` covers product/category roots, typed custom values, versions, audit fields, and event-free reconstruction. `ApprovalWorkflowPOConverterTest` covers definition configuration, route/fact/task snapshots, stage metadata, progress, versions, and audit timestamps. `OrganizationRepositoryImplTest` verifies that a conditional pending-request unique-key conflict becomes a stable organization conflict. Extend the relevant converter or repository test whenever its PO, mapper, or migration changes.

`InvoiceProfileRepositoryImplTest` locks the fail-closed rule that a missing tenant is rejected before a null identifier can produce an empty lookup.

### Integration

`BaseIntegrationTest` supplies the `test` profile, MySQL Testcontainers properties, Flyway initialization, MockMvc identity helpers, and explicit database cleanup. Integration tests are not wrapped in one rollback transaction because real commits are needed for post-commit effects and optimistic locking.

Representative integration coverage includes:

- migration and PO round trips against an empty MySQL database;
- tenant isolation in repositories and cache keys;
- optimistic-lock and unique-key conflicts mapped to `409`;
- soft deletion through `status=DELETED` without physical row removal;
- authentication, capability separation, validation, and safe error responses;
- organization bootstrap, active-member guards, paged tenant and department-member reads, deleted-department invisibility, membership-request submit/approve/reject/withdraw transitions, cross-tenant review rejection, in-tenant department traversal, database-serialized structure locks, pending-request uniqueness, and data-only tenant-access validation;
- tenant role and navigation capability changes taking effect on the next request;
- commercial overview and trend report source-scope and field-policy boundaries plus custom reports catalog filtering, current-member ownership, `reports:read`/`reports:write` route separation, optimistic replacement and soft deletion, field dependency protection, exact 501-row overflow rejection, real-MySQL typed filter/SUM aggregation, and EXPLAIN assertions for built-in and custom time-range indexes;
- dashboard preference defaults, typed time/data-scope and closed module-catalog validation, database checks, tenant/member isolation, display-only hiding, and optimistic version conflicts;
- dashboard overview UTC window/granularity boundaries, source/presentation-scope intersection, tenant-to-department predicate rebuilding, missing-department downgrade, stable fixed-card order, zero-filled totals, partial availability, hidden-field opacity, non-persisting defaults, unknown-failure propagation, and customer contact-reminder cumulative/overdue aggregation under real customer/activity/field-policy boundaries;
- Customer overview customer-first failure closure (including resolving Customer access only inside the read transaction), seven-card stable order and lifecycle totals, each source's SELF/DEPARTMENT/DEPARTMENT_SUBTREE/TENANT scope and required-field gate, Contact/Opportunity/Contract collaborator expansion without extending Invoice/Receivable/ReceivablePlan/ReturnVisit, actual hidden/masked-field opaque `UNAVAILABLE` downgrade with omitted JSON null fields, empty versus pool/deleted/cross-tenant Customer behavior, a real-MySQL concurrent commit retaining the in-flight `REPEATABLE_READ` snapshot, MySQL customer-index candidates with no full scans, and Contact `customerId` SQL drill-down under independent Customer/Contact visibility;
- custom-field metadata and dependency-safe lifecycle changes, `(tenant, objectType)` database serialization against concurrent layout and saved-view writes, deadlock-free multi-transaction acquisition for both existing and initially absent guard rows, member layout optimistic locking and active-catalog intersection, role field access and masking, inactive-member failure closure, typed saved views with one database-enforced default and stale-condition filtering, safe embedded-page rendering, and cross-tenant customization rejection;
- customer aggregate creation, patch updates including explicit phone clearing, final ASCII URL bounds, NFKC-expanded unique reservations, accent-distinct tag persistence, NFKC/case-equivalent batch removal and visible catalog grouping, V11-to-V12 tag-key backfill, custom-value execution through a personal saved view, personal stars, duplicate and nearby queries, normalized direct predicates, unmasked-field requirements, Unicode-safe and deal/audit-time masking, mutation parent consistency, visible-only hierarchy results and write-enforced depth/node bounds, native one-sided coordinate rejection, optimistic-lock conflict, soft deletion, cross-tenant not-found behavior, all four data scopes, collaborator read/write separation, authorized command mapping, and no-op exit protection, immutable custom uniqueness, tag catalogs beyond 1000 visible customers, concurrent custom uniqueness, and opposing hierarchy moves;
- customer-pool authentication and tenant isolation, versioned configuration, lightweight catalog projection without aggregate relation hydration, tenant-owner catalog access without explicit pool configuration, eligibility filtering before the defensive catalog bound, concurrent enforcement of the 500-pool tenant limit, batch list counts, same-pool parent relationships, field-filtered pool reads, independent release/write authorization, one-pool membership, transfer, daily claim limits, previous-owner cooldown, administrative assignment and deletion, persistent automatic-release rotation with a 50-pool transaction bound, and concurrent claim arbitration with one owner and one history record;
- contact aggregate and contact-pool equivalents, including independent contact tables and field catalogs, resumable V18-to-V19 tag-key migration with frozen normalization and a stable checksum, failed-backfill repair retry, tri-state customer-link/next-contact patch semantics, customer-link visibility validation, visible-target saved-view relation filtering without relationship oracles, contact scope/collaboration failure closure, pool eligibility, membership transfer, claim limit/cooldown, and bounded automatic-release arbitration;
- opportunity aggregate bootstrap and lifecycle, independent customer/contact relationship failure closure, hidden-contact oracle prevention, contact projections, typed saved-view value rejection for both query and summary, equal-boundary `BETWEEN` point ranges, typed email fragment matching, personal stars, field-protected outcome clearing, collaborator self-exit without assignment authority, open/closed outcome transitions, optimistic-lock conflicts, soft deletion, and database-backed custom uniqueness;
- Opportunity pipeline/stage catalog authorization and active-member/cross-tenant failure closure, immutable binary exact codes, lifecycle and last-active-stage checks under the tenant catalog guard, 100/100 capacity and catalog-index plan eligibility, concurrent deactivate/create serialization, customization field-write denial before any catalog lookup, V84-to-V85 invalid-history preflight/no-DDL/repaired-retry plus binary-collation/default/composite-FK behavior, trimmed REST code/name boundaries, and create/actual-move active-pair enforcement while unchanged disabled-pair ordinary updates remain compatible;
- product/category bootstrap, field-policy filtered reads and typed queries, NFKC/case code and custom-value uniqueness, category cycle/depth/dependency checks, versioned availability/ownership/soft deletion, capability separation, cross-tenant failure closure, immutable opportunity product snapshots with server-calculated discount and amount, and repeatable-read root/custom-value and root/product-line hydration across concurrent commits;
- contract/invoice/receivable bootstrap for newly created tenants, explicit route capabilities, NFKC-expanded contract-number rejection, aggregate amount precision, MySQL-safe date bounds, immutable contract product snapshots, empty-product writes without product-read authority, relationship and PRODUCT-policy failure closure on mutation responses, target-owner and collaborator assignment scopes, status-field-protected voiding, independently visible invoice relations on issue/reset/owner responses, blank-keyword field-policy behavior, invoice-profile customer checks inside transaction boundaries, invoice ceiling arbitration under contract locks, receipt and plan ceilings under the same contract lock, plan immutability after receipt history, available-plan filtering, contract customer/amount/deletion protection, stable contract-save mapping for named composite-FK races, composite same-customer persistence across later opportunity/contact moves, concurrent 100-active invoice-profile capacity and bounded profile queries, status-based invoice-profile deletion and personal-field clearing, plus authoritative CONTRACT/INVOICE/RECEIVABLE approval facts with independently filtered relationship IDs in details, pages, and batch summaries;
- activity subject and relation visibility rechecks, governed methods/templates, author-only mutable follow-ups, bounded batch atomicity, activity soft deletion and relation persistence, RETURN_VISIT field bootstrap, CRUD/simple options, customer/contact/contract same-customer enforcement including database rejection, explicit self-ownership without assignment authority, owner-scope assignment, masked field output, custom unique values, and cross-scope relationship suppression;
- business-list search keyword bounds and transport validation, fixed candidate catalogs, active/searchable/readable/unmasked policy intersection, literal wildcard escaping, normalized product-code matching, empty-candidate failure closure, and MySQL/HTTP verification of tenant, data-scope, soft-delete, public-pool, and masked/hidden-field boundaries across customer, contact, lead, opportunity, product, and return-visit lists;
- finance-list search field-aware keyword completion for contract, invoice, receivable, and receivable-plan lists, including normalized contract-number prefixes, selected invoice fields, receipt-number-only matching, exact positive installments with invalid-input empty pages, wildcard literals, and empty-candidate failure closure;
- worklist stable continuation across interleaved sources, all nine local source projections, cross-tenant/data-scope/field-policy filtering, exact and category acknowledgements, fail-closed bounded category verification, version-sensitive reappearance, `v2` cursor decoding with `WORK_TASK` exhausted, and `v3` continuation across nine independent positions;
- Work-task creator/assignee/follower/outsider/manager matrices, active-member loss, tenant-safe not-found behavior, follower-aware SQL filters, same-snapshot CRM relationship authorization before count/page, the 50-follower bound, Unicode comment normalization plus paging/author/version/soft-delete history, real owning-domain read/write/data-scope checks for all eight CRM relationship types and output suppression after access loss, explicit-new-target replacement checks plus hidden-link/no-link clear parity, immutable first-start time, terminal transitions, projection-version insensitivity to followers/comments/relationships, current-assignee-only worklist projection under the combined worklist/task-read gate, exact/category acknowledgement without lifecycle mutation, projection-sensitive reappearance, reassignment, restricted deletion, V56 paired checks/composite foreign keys/indexes, and concurrent root/comment optimistic arbitration;
- marketing-form tenant isolation, versioned configuration and status lifecycle, current customization field metadata, fully resolved Lead owner data scope, required-field activation, active-field dependency protection, canonical shared quotas for direct/alias/percent-encoded anonymous paths, strict rejection of unknown owner/tenant/lifecycle properties, form-row serialization against update/deactivation, single-connection reuse of Lead creation plus successful-submission ledger append inside one transaction, ledger-failure rollback, custom-field persistence, 6907 remapping of Lead uniqueness failures, DAY/WEEK/MONTH zero-filled success trends, deleted-form history, and SQL-level SELF/DEPARTMENT/DEPARTMENT_SUBTREE/TENANT/collaborator link pagination with accurate visible totals;
- OA_REQUEST built-in provisioning, versioned OA form lifecycle, normalized code and 200-form capacity arbitration, current field metadata/access filtering, server-derived and scalar FILE input rejection, same-tenant OA approval binding, initiator availability, active field/approval dependency protection, fail-safe deactivation/deletion, cross-tenant not-found behavior, customization-guard serialization between form activation and approval deactivation, and guard-before-definition-row ordering between approval-definition update and status change;
- immutable OA request preview/submit parity, strict rejection of caller-owned identity facts, current-field and active-route revalidation, one-connection transaction request/unique-value/approval linkage, concurrent equivalent idempotency, changed-payload conflict, failure rollback, SQL participant visibility and accurate totals, raw-envelope leak prevention plus current field hiding/masking in both OA and approval output, approval action/status linkage, unmasked-title-gated worklist `APPROVAL_PENDING` reuse, and independence from Work-task creation or state;
- OA announcement draft/update/publish/withdraw/delete transitions, Unicode code-point limits, strict typed selector and caller-owned-field rejection, active same-tenant recipient expansion and de-duplication, the 10,000-recipient overflow failure, stable snapshots across membership moves and later joins, inactive-member failure closure, same-snapshot management membership/statistics reads, root-only management/inbox/read-lock projections with zero selector-mapper calls, ordered HTTP read/write matcher enforcement before controller validation, SQL-visible inbox totals, concurrent insert-if-absent receipts preserving the first server time, shared announcement locks across recipients, membership-suspension lock ordering, current unread counts, aggregate-only manager statistics, the V57 status-only soft-delete schema, composite recipient/receipt foreign keys, and concurrent publication version arbitration;
- approval definition activation, authoritative business-record routing despite forged or omitted public facts, customization write checks plus hidden/masked snapshot responses, canonical public/internal idempotency namespace isolation, active and bounded role route resolution after stale-assignment filtering and cross-role de-duplication, lightweight visibility scans plus root-only list/batch summaries and per-object field-access caching, normalized selected-step collision rejection, immutable manager/route/task snapshots, the 5,000-task transfer boundary, `ONCE` transfer rejection for prior approvers under all step modes, dirty-task-only persistence, stale action rejection, null collection-element rejection, current business-scope enforcement for every runtime action and count, visible-record page filling with accurate totals, tenant isolation, lock-time workflow reload, active approval references, bounded stage-gate titles, the 100-field required/condition union boundary including referenced definition updates, non-empty stage-required values, searchable-field lifecycle protection, rejected/cancelled/withdrawn gate consumption followed by an explicit new attempt, running-workflow deletion rejection with preserved field/approval dependencies, terminal stage progression, and versioned author-owned comments;
- Redis-disabled health and generated OpenAPI metadata.

### Trusted-header authentication

The `test` profile enables the trusted-header adapter. Requests use positive numeric `X-Dev-User-Id` and `X-Dev-Tenant-Id` values.

Cover missing, incomplete, and invalid header pairs; missing authority; attempted authority or administrator escalation; and trusted headers combined with `prod`. Keep credentials out of assertion output and captured logs. These tests do not replace tests for the production authentication integration.

## Change-to-test map

| Change | Minimum verification |
| --- | --- |
| Aggregate or value object | Domain unit test |
| Shared result, error, or pagination contract | Shared JUnit contract test |
| Application command/query | Caller, tenant, success, and failure application tests |
| Cache behavior | Tenant-key and database fallback tests |
| PO or converter | Complete round-trip test |
| Mapper query | MySQL integration test |
| Flyway migration | Empty-database migration test |
| Security route | Anonymous, allowed, and forbidden HTTP tests |
| Error mapping | Exact status, stable code, and safe message test |
| Transactional side effect | Commit and rollback tests |
