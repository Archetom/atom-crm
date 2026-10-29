---
name: review-architecture
description: Review CRM changes for concrete DDD, subject/tenant, field-policy, aggregate, transaction, or unnecessary-abstraction defects.
---

# Review CRM architecture

Inspect the change, nearby callers/tests, [AGENTS.md](../../../AGENTS.md), and affected sections of [architecture](../../../docs/architecture.md) or [object layering](../../../docs/object-layering.md). Use [the index](../../../llms.txt) to load specialized feature contracts only when relevant.

1. Trace module dependencies and data ownership: domain remains framework-neutral, application owns orchestration/ports, adapters own technology, and `start` composes them. Check controller/repository bypasses and domain/PO/VO leakage. Preserve the existing explicit caller/connector facade contracts and streaming/export services.
2. Verify aggregate factories, named reconstruction, behavior, root/child consistency, versions and rejected transitions. Distinguish intentional persistence synchronization from mutation that bypasses business invariants.
3. Trace the verified human, connector, public capability, or durable background claim to every operation. Check current membership/capability, record scope, related-record access, field masks, tenant predicates/cache keys, and fail-closed behavior. Evaluate scope exceptions against their documented contract and `RepositoryPortTenantScopeTest`, not a new blanket prohibition.
4. Trace lock order, participating coordinators, snapshot reads, optimistic conflicts, and commit/rollback effects. Check independent after-commit cache/event callbacks and existing outbox/worker fences. Static dependency tests do not prove SQL scope, current authorization, durable recovery, or transaction ordering; locate runtime evidence.
5. Challenge new interfaces, DTOs, conversions, adapters, or inheritance when they add complexity without a current consumer. Do not redesign an established CRM boundary for style or copy a generic archetype sample rule over it.
6. Follow [verify-change](../verify-change/SKILL.md). Module/static changes require the architecture suite; affected authorization, tenancy, persistence, transactions, cache, events or worker boundaries require full database verification.

Output actionable findings with severity, file/line, triggering scenario, impact, and a concrete repair direction. Separate defects from questions and unverified risks. If none are found, report the checks and remaining coverage limits; an ArchUnit pass alone does not establish runtime security.
