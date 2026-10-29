# Contributing to atom-crm

Bug reports, documentation improvements, tests, and focused pull requests are welcome. Use Chinese or English. Discuss the code and evidence respectfully, and use synthetic data in examples. The project is distributed under the [MIT license](LICENSE).

## Choose the right place

The repositories are [atom-crm](https://github.com/Archetom/atom-crm) and [atom-crm-web](https://github.com/Archetom/atom-crm-web).

- Backend/API behavior belongs in this repository; browser behavior belongs in `atom-crm-web`.
- For a bug, use the bug report form with reproducible steps, expected and actual results, and the backend/frontend commit IDs.
- For a feature or a substantial contract change, explain the use case and existing limitations in a feature request before implementing a broad redesign.
- For usage questions, first check the [quick start](README.en.md#quick-start), [configuration](docs/configuration.md), and [test guide](docs/test-guide.md), then use a question issue if the problem remains.
- Report suspected vulnerabilities privately using [SECURITY.md](SECURITY.md), not the public issue forms.

Community support has no guaranteed response time. Never post credentials, session cookies, reset links, customer records, database dumps, private provider payloads, or unredacted logs.

## Set up and make a change

1. Fork or clone the repository and create a branch for one coherent change. Read [AGENTS.md](AGENTS.md), the applicable architecture rules, and nearby tests.
2. Use JDK 27.0.1 or newer and the checked-in Maven Wrapper 3.10.0. Follow the [quick start](README.en.md#quick-start) to run MySQL, the backend, and the separate frontend. Normal browser use starts at `/setup` and uses password login; development identity headers are optional local test tooling.
3. Keep changes within the existing adapter → application → domain boundaries. Follow the [development workflow](docs/usage-guide.md) and [object-layering guide](docs/object-layering.md).
4. Add regression coverage for changed behavior. Preserve tenant isolation, authorization, optimistic locking, transaction boundaries, and post-commit effects. Add Flyway migrations instead of editing applied migrations.
5. Update the API/configuration documentation when public behavior changes. For an HTTP contract change, coordinate the frontend OpenAPI snapshot, generated types, and caller tests; do not edit generated frontend types by hand.

Use the relevant [task workflow](llms.txt#task-workflows) when helpful. The Atom Archetype 2.3.0 engineering baseline supplies these workflows and verification tooling; CRM's explicit caller/connector facade contracts, scope exceptions, and feature-specific rules remain defined by this repository.

## Verify

Run commands from the backend repository root. See the [test guide](docs/test-guide.md) for test selection and database evidence.

| Change | Required local verification |
| --- | --- |
| Documentation only | Check links, paths, and commands; run `git diff --check` |
| Focused behavior | `sh ./mvnw -pl <module> -am test -Datom.test.skip-database-tests=true` |
| Cross-module contract | Focused tests, then `make agent-check SCOPE=fast` |
| Database, security, transactions, mapper, cache, or event boundaries | `make agent-check SCOPE=full`; requires Docker and verifies database execution reports |
| Release | `make release-verify` |

For one class, add `-Dtest=<TestClass> -Dsurefire.failIfNoSpecifiedTests=false`. `make agent-check SCOPE=affected` can select conservative checks from Git changes; a passing narrow scope does not replace the full requirements above. Direct Maven tests can skip database cases when Docker is unavailable, and `CI=true/false` does not change that gate. The full entry point disables explicit skipping and checks `VerifyDatabaseTests.java` evidence. Record any skipped tests or unavailable infrastructure in the pull request.

## Submit a pull request

Describe the concrete problem, resulting behavior, and verification. Include an issue reference when one exists. Use screenshots only when they help explain a visual change and contain synthetic data. Keep unrelated formatting or generated changes out of the patch. For changes spanning both repositories, link the companion change and record the pair of revisions tested together.

Do not commit local environment files, secrets, customer data, test reports containing sensitive output, or local IDE state. Run `git diff --check` and review the complete diff before submission. Maintainers review changes before merging; opening a pull request does not guarantee acceptance or a release date.
