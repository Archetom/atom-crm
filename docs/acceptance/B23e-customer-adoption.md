# B23e Customer adoption, identity, provenance and retention acceptance

Date: 2026-07-23

## Delivered boundary

`POST /api/v1/enterprise-data/adoption-preview` accepts only an existing Customer/version, an exact typed identity selector and an address slot. The service resolves the verified caller's active tenant and Customer write visibility, obtains the governed source observation before opening the read transaction, and issues a random one-time reference bound to that tenant/member/Customer/version. A preview stores no MySQL observation or raw provider response.

`POST /api/v1/enterprise-data/adopt` accepts only that reference, expected version, selected fact names and selected identity types. It consumes the reference, repeats the exact source lookup, rejects policy/observation drift, locks the Customer/customization root, enforces current B05 write access, reserves sorted identities, updates only the selected name/address mapping, saves the same Customer instance, and appends value-free provenance in the same command transaction. The caller never supplies a source value, tenant, actor, policy, adapter binding or locator.

`V80__create_customer_registered_identity_and_adoption_provenance.sql` provides current identity arbitration, immutable released identity history, value-free adoption headers and selections. `V81__create_enterprise_data_adoption_retention_work.sql` adds a durable leased retention task per header; the worker claims a system task before it receives the carried tenant, and only then calls the tenant-scoped physical purge. Header deletion cascades the task. Production defaults both source adoption and this worker to disabled.

## Evidence achieved

- `CONTRACT_CLOSED`: application unit tests cover policy-before-provider failure closure, no raw provider exception exposure, caller-bound preview, second exact lookup, selected name/address mapping, Customer lock, and value-free provenance.
- `DURABLE_SIMULATED`: the MySQL Testcontainers suite runs V80/V81 and proves exact identity idempotency, competing claim rejection, concurrent one-winner arbitration, rollback release, Customer soft-delete release, cross-tenant independence, no raw-value provenance columns, tenant-scoped purge, and cascade removal of retention work.
- `CONTRACT_CLOSED`: Companies House has a bounded HTTP Basic adapter mapper and a versioned `OFFICIAL_REGISTRY` policy gate. It requires a synthetic/test-only mapper constructor for unit evidence; production composition stays unavailable.

## Commands run

```bash
sh ./mvnw -q -pl application -am test -Dtest=EnterpriseDataAdoptionServiceImplTest,EnterpriseDataAdoptionRetentionWorkerTest,UseCaseOperationCompatibilityTest -Dsurefire.failIfNoSpecifiedTests=false
sh ./mvnw -q -pl infra/external -am test -Dtest=CompaniesHouseEnterpriseDataSourceTest -Dsurefire.failIfNoSpecifiedTests=false
CI=true sh ./mvnw -q -pl start -am test -Dtest=EnterpriseDataAdoptionPersistenceIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

All three passed. The last command runs against MySQL 9.7.1 Testcontainers.

## Production enablement remains closed

No shipped source can currently grant `ADOPT_SELECTED_FACTS`: GLEIF's published policy remains `EPHEMERAL_ONLY`, while Companies House needs an API key, data-rights approval, privacy review, copyright review, tenant binding, independent current-build conformance source, and a real live canary. A property, mock, synthetic fixture, mapper unit test, or adapter self-report cannot change that state. Therefore B23e does not claim `REAL_BACKEND_CONFORMANT`, `LIVE_CANARY_PASSED`, or `PROD_READY` for Customer adoption.
