---
name: verify-change
description: Select and run CRM checks using explicit database-test gating, conservative affected scopes, and actual execution/report evidence.
---

# Verify a CRM change

Run from this application's root with the JDK/Maven versions in [AGENTS.md](../../../AGENTS.md). Inspect the complete change, new/deleted files, callers, and affected configuration before selecting the union of required checks.

1. Use `make agent-check` for the conservative default `full`. `SCOPE=affected` is optional: it considers staged/unstaged/untracked/renamed/deleted files, narrowing only recognized documentation or eligible Java test changes in one domain/application module. Production, public/shared, POM, cross-module, sensitive, or unknown changes select full. Missing Git context/history or optional Python 3 also falls back to full. Path selection does not prove semantic safety.
2. Get deliberate focused feedback with `sh ./mvnw -pl <module> -am test -Datom.test.skip-database-tests=true`; add `-Dtest=<Class> -Dsurefire.failIfNoSpecifiedTests=false` for a single test. Then run broader checks required below. Never treat `CI=false` as a database skip or `CI=true` as proof that database tests ran.

| Changed contract | Required evidence |
| --- | --- |
| Documentation/instructions/Skills only | Facts, paths/links, and `make agent-check SCOPE=docs`; no Java build |
| Domain behavior | Aggregate/value/policy valid and rejected-case tests; fast suite for cross-module or static-boundary impact |
| Application behavior | Subject, membership/capability/scope, success/failure tests; full for affected security/transaction/cache/event behavior |
| HTTP contract | Validation, stable status/code, response/OpenAPI and relevant frontend contract tests; full for authorization/tenant or persistence behavior |
| Persistence/migration | Complete round trip/reconstruction, prior-version upgrade, tenant isolation, optimistic locking, and full MySQL evidence |
| Identity/authorization | Human/connector separation, invalid identity/current membership/capabilities, record/field policies, and full MySQL evidence |
| Transactions/events/cache/workers | Commit/rollback, independent effects, scope/fallback, required ordering or stale-worker fences, and full checks |
| Architecture/module dependencies | `make agent-check SCOPE=fast`, including `ArchitectureBoundaryTest` and `RepositoryPortTenantScopeTest`; full when runtime behavior is affected |
| Build/release | Full check and the existing release gate's metadata/database requirements |

3. `fast` explicitly sets `atom.test.skip-database-tests=true`. `full` requires Docker and uses the release gate's `clean install` with skipping disabled, then `scripts/VerifyDatabaseTests.java` rejects missing, skipped, failed, or errored database tests. Direct `sh ./mvnw test` may skip database tests without Docker; successful Maven exit alone is insufficient evidence.
4. Read the process exit status and JSON's requested/selected scope, check results, and actual report paths. Logs go to standard error. Diagnose failures from current reports; do not weaken checks, repeat successful suites without new evidence, or present unavailable infrastructure as a pass. Report commands, scope, results, and any unverified behavior.

Acceptance: required checks actually ran successfully or each missing check is identified with its cause. There is no archetype-generation or sample-cleanup step for this established CRM application.
