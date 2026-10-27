# Connector integration

The repository provides connector registration, credential lifecycle, authenticated ingress, and a durable normalized call-fact inbox. It also contains PBX transport and crash-recovery conformance runners. These foundations do not provide a production telephone service, a public outbound-call API, or a recording playback service. A transport probe or passing simulator does not enable those capabilities.

## Authentication and tenant ownership

Human administrators manage connectors through the [connector APIs](api-reference.md#connector-security-and-control-plane). Reads require current tenant membership and `connectors:read`; mutations require `connectors:manage`. Registration binds a connector to one tenant. Ordinary request bodies cannot supply connector credentials, tenant identity, provider configuration, or an authenticated actor.

Connector authentication uses a dedicated bearer credential in the form `connectorRef.keyId.secret`. Credential lookup uses the exact `(connectorRef, keyId)` pair; registration lookup uses the complete `connectorRef`. The separate minimal security projections exist only to authenticate and resolve context. They do not grant unscoped access to business repositories.

`AuthenticatedConnector` contains only the verified opaque reference. It has no actor, tenant, roles, or authorities. `ConnectorGuard` resolves the active registration and derives its tenant; ingress commands call `lockActive` inside their command transaction before business writes. A disabled/deleted connector or revoked credential fails closed. Human callers and background workers must never be synthesized from connector input.

The body-free `POST /api/v1/connector-ingress/probe` verifies this authentication boundary. Source-address admission follows the explicitly trusted-proxy rules in [configuration](configuration.md#public-lead-intake). An untrusted forwarded header cannot select a rate-limit bucket.

## Normalized facts and durable recovery

Native provider channels, bridges, connection generations, SIP endpoints, headers, variables and payloads terminate in the infrastructure adapter. The inward contract contains only typed, bounded normalized facts and connector-scoped identities. The core inbox validates current registration, deduplicates stable fact identities, and applies tenant-scoped state under transaction and version checks. Provider retries cannot overwrite newer state.

Future production adapters must preserve these reliability boundaries:

- Persist a native-event journal before acknowledging delivery. Keep stable event/session identities across reconnects and fence an old stream owner before a replacement can publish.
- Distinguish an observation gap from a completed lifecycle. An active-object snapshot proves only what it observes; it cannot prove that an unobserved call never existed or ended.
- Commit a command ledger in `DISPATCHING` before provider I/O. `AT_MOST_ONCE_START` must not redispatch an ambiguous attempt. `IDEMPOTENT_START` can repeat only the exact governed key, request digest and provider idempotency window.
- Keep provider acceptance, lifecycle observation, result delivery and reconciliation as separate durable facts. A timeout, ordinary `404`, missing callback, expired lease or retry count does not prove absence and cannot authorize redial.
- Reconcile through bounded read-only provider queries. Close ambiguity only with authoritative coverage and identity evidence; retain uncertainty when that proof is unavailable.

Commands use participating coordinators for atomic composition. No database transaction remains open across provider I/O. Durable external delivery uses a ledger/outbox; an after-commit in-process event alone is not a delivery guarantee.

## Sensitive data requirements

Production calling requires a governed envelope-key provider, a versioned tenant policy and hard retention ceilings. Human input selects an authorized CRM record and phone field; the application resolves the current readable field and creates a purpose-specific encrypted address snapshot. Plaintext numbers, dial strings and recording references must not enter ordinary facts, events, logs, error messages, audit payloads or arbitrary maps.

Private adapters isolate sensitive values from ordinary journals. Fingerprints use tenant- and purpose-separated HMAC, not an unkeyed digest of a low-entropy phone number. Plaintext holders use redacted string representations and cannot be serialized as ordinary JSON. Optional raw replay material belongs in a separately keyed encrypted store with an absolute expiry.

Reliability gaps never extend sensitive retention. Key rotation prioritizes expired-value deletion; restoring a backup requires an expiry sweep before service. Core deletion, adapter deletion, provider-controlled CDR/log/spool removal, key destruction and backup expiry remain distinct evidence. A single success flag must not imply physical destruction across every surface.

## Recording and readiness requirements

Recording support is not enabled by the current runtime. Any future implementation must keep live recording, call termination, recording-finished input and stored-object readiness separate. Only an exact connector-scoped, closed, readable, format-valid and immutable object may become available.

Every full or single-range request must revalidate current human membership, record scope and field/media policy. The application records the minimal access attempt in a short transaction, then opens a typed private stream outside it. The adapter returns bounded metadata and a closeable/cancelable byte source; provider URLs, paths, HTTP headers, SDK objects and full buffers remain private. There is no browser bearer grant, redirect or public media URL.

Native exact-offset reads are required for ranges. Invalid or unsupported ranges cannot fall back to a full object. Client disconnect, open/idle/stream deadline, byte limit, retention cutoff and removal cancel the upstream handle without transparent reopen. Source/member/tenant/connector/process admission must preserve a separate deletion reserve. Expiry, durable deletion, authoritative absence and open-handle drain are separate completion conditions.

Readiness is per operation, current adapter build and private binding revision. It intersects lifecycle support, durable recovery, isolation, policy, quotas, key access and fresh conformance evidence. A ready probe, a valid credential or a working transport does not establish call or recording readiness. Missing evidence disables the affected operation without weakening CRM authorization.

## Verification

Use the [testing guide](test-guide.md) for JDK 25, Maven and Docker requirements. Relevant boundaries are covered by `ConnectorGuardTest`, `ConnectorAuthenticationServiceTest`, `CallFactInboxServiceImplTest` and `ConnectorIntegrationTest`.

The maintained entry points are:

| Script | Evidence scope |
| --- | --- |
| [`pbx-transport-canary.sh`](../scripts/pbx-transport-canary.sh) | Isolated Asterisk transport probe |
| [`call-inbox-conformance.sh`](../scripts/call-inbox-conformance.sh) | Core durable inbox behavior |
| [`call-adapter-durability-check.sh`](../scripts/call-adapter-durability-check.sh) | Private-adapter recovery and independent durable oracle |
| [`call-conformance-matrix.sh`](../scripts/call-conformance-matrix.sh) | Combined call conformance checks |

Read each script's arguments and environment prerequisites before running it. Test-private drivers, native frames and oracle projections do not become production ports. Keep existing scenario/report identifiers stable when changing implementation; report compatibility is independent of document naming. Release checks must state which tests and live lanes actually ran, and must not promote model or transport evidence to production approval.
