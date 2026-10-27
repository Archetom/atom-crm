# B23d GLEIF runtime and live-canary acceptance

Date: 2026-07-23

## Delivered boundary

`GleifEnterpriseDataSource` is an opt-in GLEIF Level-1 JSON:API adapter. It permits only the fixed `https://api.gleif.org/api/v1/` origin, rejects redirects, bounds response bodies, issues one request per operation without inline retry, and discards every native field outside the B23 profile. It accepts only the five B23-O/2 categories and maps the three pinned RA rows only through the current RA catalog digest.

`V79__create_enterprise_data_source_quota.sql` provides one MySQL row per provider for serialized cross-instance quota consumption. The quota operation receives a verified tenant but stores no tenant, query, identifier, response, source locator or request history.

Source readiness is fail-closed. It requires all of the following: explicit tenant and jurisdiction binding; rights-approval reference; matching policy and RA catalog version/digest; a positive quota/window and durable quota port; and an owner-only, runner-written report matching the current adapter build plus all `O2-01..12` with `DURABLE_SHARED` quota evidence. `atom.enterprise-data.gleif.enabled` defaults to false in production. The shipped policy remains `EPHEMERAL_ONLY`.

## Real canary

`B23GleifLiveCanaryRunner` was run against GLEIF's public HTTPS API. It requested a bounded GENERAL-category page only in process memory, performed one exact lookup through the runtime adapter, and emitted a 0600 JSON report with no registry value, request, response, source revision, URL parameter, account, or credential. The report passed its safe-schema check.

The repeatable entry point is now explicit-consent-only:

```bash
scripts/b23-gleif-live-canary.sh \
  --allow-live-canary \
  --report /absolute/path/b23-gleif-live.json
```

It builds the current `infra/external` runtime classpath, refuses an existing report, performs no output serialization other than the runner's owner-only JSON, and cannot accept a selector, tenant, endpoint, credential or arbitrary request data. A fresh 2026-07-23 execution again passed and emitted a 506-byte `0600` manifest containing only catalog/policy digests, fixed check IDs and expiry.

Evidence achieved: `LIVE_CANARY_PASSED` for the isolated real-adapter `O2-12` subset (`HTTPS_BOUNDARY`, `EXACT_LOOKUP`, `ALLOWLIST_MAPPING`, `SAFE_REPORT`). The runner writes `adapterBuildDigest=CANARY_LIVE_ONLY`, `quotaMode=CANARY_LOCAL_ONLY`, and only `passedScenarios=["O2-12"]`; therefore the runtime rejects it and this is not source readiness or `PROD_READY`.

## Not closed

- Full current-build `O2-01..12` conformance and a `DURABLE_SHARED` report.
- Deployment-specific source-rights/data-protection approval and tenant binding.
- Current-build `O2-01..12` and `DURABLE_SHARED` evidence remains required before GLEIF can be `REAL_BACKEND_CONFORMANT` as a source.
- A separate B23e Customer-adoption implementation is complete but has no approved `ADOPT_SELECTED_FACTS` real-source policy; Companies House remains `NOT_READY` without API key, reviews and independent live evidence.
