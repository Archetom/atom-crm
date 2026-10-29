---
name: create-feature
description: Implement or change a CRM business use case across only the domain, application, API, and adapters it needs, preserving current authorization and transaction contracts.
---

# Create a CRM feature

Use [AGENTS.md](../../../AGENTS.md) for mandatory boundaries and the relevant sections of [development workflow](../../../docs/usage-guide.md). Run commands from this CRM repository root.

1. Identify the actor or non-human subject, business outcome, rejected cases, and authoritative feature contract. Check [the index](../../../llms.txt) for the relevant connector, enterprise-data, file, or CRM guide. Resolve assumptions that change behavior; record non-obvious decisions using the optional [domain-contract guide](../../../docs/domain/README.md).
2. Map only necessary changes: `domain` for business behavior/repository ports; `application` for use cases, authorization and output ports; `api` for contracts; the relevant `infra` adapter for technology/transport; `start` for composition. Reuse existing aggregates and coordinators. Do not create every DDD object, a new module, or a speculative abstraction by default.
3. Preserve the established subject path: human facades/use cases accept verified `api.context.AuthenticatedCaller`; connector ingress uses `AuthenticatedConnector` and `ConnectorGuard`; background work derives scope from its durable claim. Never manufacture a human caller for another kind of work. Apply current membership, capability, record scope, relationship visibility and field policy where the feature requires them.
4. Implement aggregate behavior and rejected transitions at their owning layer. Preserve named reconstruction, optimistic versions, command/query templates and lock ordering. Compose atomic work through the existing participating coordinators; do not nest independent commands. Register cache and event effects separately after commit.
5. Add only consumed port/DTO/assembler changes. Align persisted state, PO/converter/mapper and forward migrations when relevant. Use [add-api](../add-api/SKILL.md) or [add-migration](../add-migration/SKILL.md) for those boundaries. Preserve streaming/export paths and privacy/readiness rules.
6. Add focused tests for valid and rejected behavior, current authorization, tenant scope and affected rollback/concurrency cases. Update the existing feature contract and follow [verify-change](../verify-change/SKILL.md).

Acceptance: the requested feature works through its actual entry point, unauthorized or invalid work is rejected before effects, no unused object/port/conversion was added, and required checks pass or are explicitly reported as unverified.
