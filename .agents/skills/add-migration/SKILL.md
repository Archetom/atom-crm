---
name: add-migration
description: Evolve CRM Flyway schema and persistence mappings with existing-data upgrade, tenant isolation, concurrency, and reconstruction evidence.
---

# Add a CRM migration

Use [persistence invariants](../../../AGENTS.md#state-transactions-and-persistence), the [migration workflow](../../../docs/usage-guide.md#add-a-database-migration), and the relevant [testing guidance](../../../docs/test-guide.md).

1. Inspect current migration versions in `infra/persistence/src/main/resources/db/migration` and choose the next unused version. Preserve every applied SQL/Java migration and checksum. Flyway remains the sole schema source.
2. State old/new schema and existing-row assumptions. Assess backfill, nullability, tenant-qualified uniqueness, indexes/foreign keys, deployment compatibility, and current business semantics. Use additive evolution when old/new application versions must coexist; do not infer permission to discard data.
3. Align PO, mapper SQL/result mappings, converter, and named aggregate reconstruction. Persist a field in the aggregate only when it is domain state. Preserve tenant predicates, root/child transaction and lock order, optimistic compare-and-increment, version/audit restoration, and reconstruction without events. Check legitimate scope exceptions against `RepositoryPortTenantScopeTest`; do not add an unscoped shortcut.
4. Extend field round-trip and invalid-data tests. Add MySQL evidence for fresh schemas and the affected prior-version upgrade with representative rows. Follow the existing isolated upgrade-schema fixtures. For resumable Java backfills, test failed-phase retry and keep historical transformation logic/checksums independent of changing domain policies.
5. Exercise cross-tenant reads/writes, stale-version rejection, and relevant relationship/field-policy paths. Run focused tests first, then `make agent-check SCOPE=full`; it requires Docker and verifies actual database test execution. `CI=true/false` does not enable or disable CRM database tests.

Acceptance: the intended upgrade preserves existing data and immutable migration history, mappings agree with stored state, tenancy/concurrency hold, and required MySQL tests ran without skips. Report any deployment assumption or unverified upgrade path explicitly.
