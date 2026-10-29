# Domain contracts

Use this directory only when a CRM feature needs business context that is not already clear in its maintained guide or tests. Existing contracts remain in [architecture](../architecture.md), [development workflow](../usage-guide.md), [API](../api-reference.md), and specialized guides linked from [the index](../../llms.txt). Do not move or duplicate those contracts merely to adopt a new document layout.

One short file per business area is enough. Simple CRUD needs no separate glossary, context map, or state-machine document. Include only relevant sections:

| Section | Record |
| --- | --- |
| Terms and ownership | Business meaning, identities, relationships, and the context responsible for decisions |
| Confirmed facts | The decision source or established behavior; distinguish implementation evidence from a new product decision |
| Open assumptions | The unresolved question, its effect, and who can confirm it; never silently enforce it as a rule |
| Invariants | Stable identifiers, prohibited outcomes, enforcement points, and executable test references |
| State transitions | Operation, source/target state, current-authorization/relationship guards, and observable effects |
| Events and effects | The fact represented, publication timing, consumers, privacy, durability, and retry guarantees |

Reference tests by class/method so they are easy to find with `rg`. A passing test confirms implementation behavior, not that an unconfirmed business assumption is correct. For scope-sensitive features, state which current human membership, connector registration, public capability, or durable claim establishes access; do not substitute a generic user example for the CRM contract.

When a feature changes a recorded invariant, update its description and regression test in the same review. Cover rejected outcomes as well as success at the lowest useful layer; persistence, current authorization and transaction claims need the actual MySQL evidence described in the [testing guide](../test-guide.md). Retire obsolete statements instead of accumulating conflicting versions.

This guide adds no business rule and requires no new framework. Engineering baseline upgrades preserve the application's existing business documents and Flyway history; new contracts are written only for decisions the CRM actually adopts.
