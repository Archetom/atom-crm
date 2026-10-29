# AGENTS.md

Java 27 / Spring Boot 4.1.2 CRM, with Maven Wrapper 3.10.0 and atom-common 1.2.0. Engineering tooling follows Atom Archetype 2.3.0; this guide owns the CRM-specific contracts. Complete and verify changes within these boundaries.

## Execution and context

- Finish authorized, reversible work without routine approval. Ask only when a missing decision blocks correctness or scope; continue independent work while waiting.
- Preserve unrelated work; reuse nearby code/tests.
- Read gitignored root `AGENTS.local.md` explicitly if present. Check applicable `AGENTS.md` / `AGENTS.override.md` along edited paths.
- Use `rg` for contracts/callers/tests; read relevant sections. Resolve stale docs against code/tests without weakening invariants.
- Delegate bounded independent work when useful; assign distinct files and review results.

| Need | Entry point |
| --- | --- |
| Setup/configuration | [README](README.md), [configuration](docs/configuration.md) |
| Modules/security/transactions | [architecture](docs/architecture.md) |
| Ownership/naming | [object layering](docs/object-layering.md) |
| Endpoints/implementation | [API](docs/api-reference.md), [usage](docs/usage-guide.md) |
| Verification | [tests](docs/test-guide.md), nearby tests |
| Features/evidence | [llms.txt](llms.txt); plans do not prove implementation |

## Skills

- Read invoked/relevant `SKILL.md` and needed references. User instructions override skill defaults.
- Store workflows in `.agents/skills/<name>/SKILL.md` with triggers, inputs, outputs and checks. Keep invariants here, contracts in docs; avoid duplication.
- If a skill blocks work/changes scope, cite the exact file/instruction and why it applies. Do not infer approval requirements from suggestions.
- Use [create-feature](.agents/skills/create-feature/SKILL.md), [add-api](.agents/skills/add-api/SKILL.md), [add-migration](.agents/skills/add-migration/SKILL.md), [review-architecture](.agents/skills/review-architecture/SKILL.md), or [verify-change](.agents/skills/verify-change/SKILL.md) for the matching task. Record non-obvious business decisions using the optional [domain-contract guide](docs/domain/README.md); keep existing feature contracts in their maintained guides.

## Architecture and naming

- Dependencies: adapters -> application -> domain. `domain` cannot import `api`, `application`, `shared`, `infra`, Spring, servlet, MyBatis or Redis.
- `application` owns orchestration/output ports; infra owns adapters/technology types; `start` composes them. `shared` has no domain concepts.
- Controllers use facade/use-case boundaries, never repositories/application VOs directly. Preserve streaming/export services and packages.
- `api.context.AuthenticatedCaller` and `AuthenticatedConnector` are trusted server-side subjects used by the existing facade contracts. REST maps verified authentication through the matching caller/connector mapper; request DTOs cannot construct these subjects.
- Use API `*Request`/`*Response`, application `*VO`, persistence `*PO`/`*POConverter`; API mapping uses `*Assembler`. Name ports by capability; avoid `I*`, concrete `Abstract*` beans and vague `Manager`/`Helper`/`Data`/`Model`.
- Use command/query templates below. Keep `UseCaseOperation` in `application/operation` with stable operation/error-code scenes.

## Security and tenancy

- Authenticated human use cases validate `AuthenticatedCaller` through `CallerGuard`. Enforce current membership, authority, record scope and field policy; navigation is not authorization.
- Tenant-owned repository/cache access requires verified tenant context and fails closed without it. Include tenant in cache keys; never add identity/tenant ThreadLocals.
- Preserve only established authentication, installation, actor-global, public-form, background-work and global reference/control-plane exceptions. Read their contracts and domain `RepositoryPortTenantScopeTest`. Login locators/public capabilities/durable claims resolve context, never unscoped business access. Public forms revalidate persisted owner authorization.
- Connector ingress uses `AuthenticatedConnector` without actor/tenant/authorities. Credential/identity lookups use exact `(connectorRef, keyId)` / full `connectorRef` and separate minimal projections. `ConnectorGuard` resolves active registration; call `lockActive` in the command transaction before writes. Never synthesize human callers for connectors/workers.
- Normal bodies/headers cannot establish actor/tenant/authority/administrator/connector identity. Connector locators belong in dedicated authentication material and match verified credentials. `X-Dev-User-Id`/`X-Dev-Tenant-Id` stay in dev/test; production cannot enable trusted headers.
- Filters/sorting use trusted metadata/current field policy, never request-controlled SQL identifiers/expressions. Outputs respect hidden/masked fields.
- Never log passwords, tokens, reset codes, secrets, sensitive requests or private provider payloads. Public errors expose stable codes/safe messages. Preserve feature privacy/readiness contracts.

## State, transactions and persistence

- Aggregates expose behavior, no public setters. Factories create; `reconstitute` restores named snapshots/version/audit fields without events.
- Save synchronizes the same aggregate, preserving events. Version conflicts cannot overwrite newer state; children share root transaction/version checks.
- Soft deletion uses each aggregate's `status=DELETED`, never `@TableLogic`/`deleted_time`. Only Flyway initializes schemas; add migrations, never edit applied ones. Align migrations, POs, converters, mapper XML and reconstruction.
- `CommandServiceTemplate`: `validate`/`prepare` precede an independent transaction around `execute`/`onSuccess`. Atomic composition uses participating coordinators, not nested commands. `QueryServiceTemplate`: independent read-only `REPEATABLE_READ` snapshot.
- Cache mutation/events use separate `AfterCommitExecutor` registrations; rollback exposes neither. Events are published after commit: use ordinary `@EventListener` consumers. Durable delivery requires an outbox.
- Database is authoritative; Redis optional. Use `DomainException`, `ApplicationException`, `NonRetryableApplicationException` by ownership. Never catch `Throwable` or suppress unexpected failures.

## Verification and completion

Run from the root with JDK 27/Maven Wrapper 3.10.0:

| Change / purpose | Verification |
| --- | --- |
| Docs/instructions | Links, paths/commands, stale guidance, `git diff --check`; no Maven build |
| Focused behavior | `sh ./mvnw -pl <module> -am test -Datom.test.skip-database-tests=true` |
| Cross-module contract | Focused tests, then `make agent-check SCOPE=fast` |
| Database/security/transaction/mapper/cache/event boundary | `make agent-check SCOPE=full`; requires Docker and verifies actual MySQL test execution |
| Build/release | `sh ./mvnw clean install` / `make release-verify` |

Modules: `api`, `domain`, `shared`, `application`, `infra/external`, `infra/security`, `infra/persistence`, `infra/rest`, `infra/facade`, `start`. Single-class runs add `-Dtest=<TestClass> -Dsurefire.failIfNoSpecifiedTests=false`.

`sh ./mvnw test` runs MySQL Testcontainers tests if Docker is usable, otherwise skips them. `-Datom.test.skip-database-tests=true` explicitly skips; `CI=true/false` does not gate them. Build success with skipped tests is not database verification.

`make agent-check` defaults to `full`: the existing release gate requires Docker, runs `clean install` with database skipping disabled, and checks reports with `scripts/VerifyDatabaseTests.java`. `fast` explicitly skips database tests; `docs` checks Git whitespace. Opt-in `affected` uses Git changes to select conservative checks, falling back to full for production, sensitive, cross-module, unknown, or unavailable change context. Its optional Python 3 selector also falls back to full when unavailable. Read the JSON scope/result and [testing guide](docs/test-guide.md); a smaller passing scope does not certify omitted tests.

Cover changed behavior. Repeat/expand checks only for new changes, failures or unresolved concerns. Never weaken security/tenancy for tests.

Complete scope, docs, diff review and checks. Report results, actual checks and blockers/coverage gaps in the user's language.

Guidance: [OpenAI prompting](https://developers.openai.com/api/docs/guides/latest-model#prompting-best-practices), [AGENTS.md discovery](https://learn.chatgpt.com/docs/agent-configuration/agents-md), [skills](https://learn.chatgpt.com/docs/build-skills).
