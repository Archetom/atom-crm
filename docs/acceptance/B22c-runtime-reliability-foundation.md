# B22c call-runtime durability foundation acceptance

Date: 2026-07-23

`V82__create_call_runtime_reliability_foundation.sql` establishes the first core-side MySQL records required by B22-R: tenant/connector-scoped interaction projection, human request binding, one start outbox per interaction, fact inbox deduplication and connector observation gaps. The schema stores only opaque IDs, fixed state, binary digests and lease metadata. It has no party address, raw PBX frame, native provider error, recording reference, media URL, credential or encryption payload column.

Every B22 record binds `(tenant_id, connector_id)` back to the immutable connector registration through a composite foreign key. Request bindings also bind their initiating actor and interaction to the same tenant. The outbox is uniquely constrained by `(tenant, connector, commandId)` and one command per interaction; the inbox is uniquely constrained by `(tenant, connector, deliveryRef)`. Leases have explicit state/fence/nullability checks so an unleased record cannot retain an active owner.

`CallFactInboxService` is a private normalized-fact boundary, not an HTTP or provider ingress endpoint. It accepts exactly two 32-byte opaque values only after `ConnectorGuard` has resolved the active connector; the command transaction locks that registration before the first inbox write. `CallFactInboxRepositoryImpl` requires that transaction, makes MySQL's composite unique key the final multi-replica arbiter, and returns only `APPLIED`, `DUPLICATE`, or a safe closed `CONFLICT`. It stores neither the provider fact nor any telephone/media value.

Verification:

```bash
CI=true sh ./mvnw -q -pl start -am test \
  -Dtest=ConnectorIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false

tmp_report=$(mktemp /private/tmp/b22-core-inbox.XXXXXX.json)
sh scripts/b22-core-inbox-conformance.sh --report "$tmp_report"
```

The MySQL 9.7 Testcontainers run passed 5 tests and applied V82. It covers connector lock ordering, exact opaque-material validation, duplicate/conflict closure, concurrent replay and two-tenant isolation. The runner emits an owner-only, value-free `B22-CORE-INBOX/1` JSON manifest with `evidenceLevel=DURABLE_SIMULATED`, `PBX_REAL=BLOCKED_RUNTIME_CONFORMANCE`, `LINE_REAL=BLOCKED_EXTERNAL`, and `productionReady=false`.

This does not prove a PBX adapter, durable private journal/ledger, Stasis fencing, two-leg media, `AMBIGUOUS` reconciliation, encrypted sensitive payload, recording/media, retention/deletion, a real external line, or production readiness. B22 overall therefore remains `CONTRACT_CLOSED`; this isolated core lane is only `DURABLE_SIMULATED`.
