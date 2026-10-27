# Enterprise data integration

Enterprise data supports bounded organization lookup and an explicit Customer adoption boundary. It does not perform background enrichment or persist raw provider profiles. Production sources and adoption retention are disabled in the shipped configuration; ordinary Customer operations remain available when a source is not ready.

## Runtime support

| Component | Current behavior |
| --- | --- |
| Synthetic source | Deterministic `dev`/`test` fixture; no real organization or person data and no production readiness |
| GLEIF | Bundled bounded runtime adapter, default off; requires approved tenant/source policy, durable shared quota and a current full conformance report |
| Companies House | Bundled bounded runtime adapter, default off; production composition and policy resolver remain `NOT_READY` pending independent live-conformance evidence |
| Customer adoption | Implemented preview, selected-fact transaction, identity reservation and value-free provenance; requires a separate approved adoption policy |
| Provenance retention | Durable tenant-scoped worker, default off |

The [HTTP contract](api-reference.md#enterprise-data-lookup) requires current membership and `enterprise-data:read`. A request selects a governed name/jurisdiction or exact registry identity; it cannot select a tenant, provider, account, credential, endpoint, quota plan, attribution text or usage permission. Customer adoption additionally rechecks current Customer write scope and field access.

## Identity and observation contract

The identity key is `(identityScope, registryScheme, canonicalValue)`. Legal jurisdiction, name, address, entity status, identifier standing and source are separate facts. Scope and scheme select an exact validator; generic identifier normalization is not sufficient.

| Scope / scheme | Validation boundary |
| --- | --- |
| `CN_UNIFIED_SOCIAL_CREDIT_CODE` / `CN_USCC` | Governed department/category and administrative-division segments, characters and check digit |
| `GLOBAL_LEI_SYSTEM` / `LEI` | Scheme-specific length, characters and check digit |
| `UK_COMPANIES_HOUSE` / `GB_CH_NUMBER` | Eight ASCII characters, explicit number-class catalog, preserved prefixes and leading zeros |

Unknown scope/scheme pairs and Companies House prefixes fail closed. GLEIF secondary registry identities require a pinned registration-authority catalog; the admitted `RA000585`, `RA000586` and `RA000587` rows each validate the corresponding Companies House number class before producing an identity. The RA code itself is not a domain identity field.

The observation allowlist contains registered identities and their standing, optional legal jurisdiction, registration name, coarse entity status, an optional typed formation date, one optional registered address, partial-data indication, source kind/update metadata, observation/expiry times, usage mode and controlled attribution. Provider IDs/locators, people, contacts, ownership, relationships, risk, raw statuses, credentials and arbitrary extension maps never cross the adapter boundary.

Organization qualification happens before mapping values outward. GLEIF admits only `GENERAL`, `FUND`, `BRANCH`, `RESIDENT_GOVERNMENT_ENTITY` and `INTERNATIONAL_ORGANIZATION`; sole proprietors, missing categories and unknown categories fail closed. Companies House qualification uses its governed organization-type catalog. Organization eligibility, entity status and identifier standing are independent checks.

## Policy, transport and readiness

`EnterpriseDataSource` reports technical capability and typed facts. A separate `EnterpriseDataReadPolicyResolver` resolves tenant-bound source kind, positive TTL, maximum usage and controlled `SEARCH_RESULT`/`DETAIL` attribution. Missing policy fails before source capability or query calls. The application validates source kind, exact identity, standing, candidate limits and returned shape; the source cannot grant rights to its own output.

Search is one bounded page with at most 20 candidates and no provider continuation token. Readiness and observation lifetime are at most five minutes. Runtime operations use at most one provider request with no inline retry, redirect following or provider fallback. HTTPS origin, request deadlines, response size, content type and schema are bounded. Provider authentication failures become safe source unavailability rather than caller authentication errors. Logs and reports do not retain registry queries, raw responses, credentials or native errors.

GLEIF readiness intersects current build evidence, explicit tenant/region binding, source-rights approval, versioned policy and RA catalog digests, positive TTL and durable provider-wide plus tenant admission. The MySQL shared quota stores admission state without query or identifier history. Production requires an owner-only external report matching the current adapter and the full required scenario set. A successful live transport canary supplies only partial evidence and is explicitly rejected as a production readiness report.

Relevant configuration groups are `atom.enterprise-data.synthetic`, `atom.enterprise-data.gleif`, `atom.enterprise-data.companies-house` and `atom.enterprise-data.adoption-retention`. Their implementation properties are in the [GLEIF runtime](../infra/external/src/main/java/io/github/archetom/crm/infra/external/gleif/runtime) and [Companies House runtime](../infra/external/src/main/java/io/github/archetom/crm/infra/external/companieshouse/runtime). Do not enable a source by setting a single flag while evidence, rights or policy remains absent. Secrets and private report paths belong in deployment secret/configuration stores.

## Explicit Customer adoption

Ordinary capabilities, search and lookup remain `EPHEMERAL_ONLY`: responses are not cached or persisted and do not write Customers, Activities, events, reports or worklists. The separate adoption endpoints require both an approved `ADOPT_SELECTED_FACTS` read policy and a compatible adoption policy. A ready lookup does not grant adoption permission. The bundled GLEIF source policy is fixed to `EPHEMERAL_ONLY`, and the default adoption-policy resolver returns unavailable. Enabling GLEIF lookup or the retention worker does not enable adoption; a deployment must supply independently approved read and adoption policy implementations.

A preview is short-lived, caller-bound and one-time. Adoption consumes an explicit selection against the expected Customer version, performs a fresh exact source lookup before the write transaction, then revalidates current membership, Customer scope, field policy and policy/evidence bindings. Customer values, registry identity reservation and provenance commit atomically. A stale version, expired preview, changed rights or conflicting identity rolls the complete command back.

One Customer may hold multiple identities; one exact identity can belong to at most one non-deleted Customer per tenant. `ACTIVE` and `IN_POOL` retain the claim. A dedicated binary unique constraint arbitrates concurrent claims; pre-checks cannot replace it and conflicts do not reveal the occupying Customer. Soft deletion releases the active claim in the same transaction while retained history remains governed by its own policy.

Provenance retains policy/evidence references, actor and Customer versions, selected target locators and retention deadlines. It does not store the external profile, selected external values or a raw-response digest. The retention worker claims an exact durable work item, obtains its explicit tenant context and performs bounded deletion. Background work never impersonates a human caller. Retention, current adoption rights and ordinary Customer storage are distinct responsibilities.

## Verification

Use `EnterpriseDataQueryServiceImplTest`, `EnterpriseDataAdoptionServiceImplTest`, `EnterpriseDataComposedFixtureTest`, `GleifEnterpriseDataSourceRuntimeTest` and `CompaniesHouseEnterpriseDataSourceTest` for policy, mapping, transport and failure boundaries. MySQL evidence includes `EnterpriseDataIntegrationTest`, `EnterpriseDataSharedQuotaIntegrationTest` and `EnterpriseDataAdoptionPersistenceIntegrationTest`; confirm they actually execute under the [database test gate](test-guide.md#commands).

[`gleif-live-canary.sh`](../scripts/gleif-live-canary.sh) requires explicit live-canary consent and the script's documented environment. It keeps public-registry values in memory and writes only a safe partial report; it neither accepts arbitrary deployment selectors nor enables production. Synthetic mapper tests, durable multi-replica simulators, HTTP leak tests and current live evidence prove different properties. Preserve stable scenario/report identifiers, and report skipped or unavailable lanes explicitly.
