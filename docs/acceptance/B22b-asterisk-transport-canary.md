# B22b Asterisk ARI/Stasis transport-canary acceptance

Date: 2026-07-23

## What was exercised

`scripts/b22-pbx-canary.sh` starts a disposable, loopback-bound Asterisk 20.15.2 container and two SIPp UAS software endpoints on an isolated Docker network. It generates an ARI password per run, copies the image's own seed configuration into a temporary directory, applies the repository-owned ARI/PJSIP overlay, validates authenticated ARI HTTP readiness, opens an ARI WebSocket subscription, originates both disposable endpoint channels into `b22-canary`, verifies two `StasisStart` events, then creates an actual mixing bridge and verifies both `ChannelEnteredBridge` events plus the bridge's two exact current members. Native channel/bridge IDs and helper output remain only in the cleaned temporary directory. It emits a mode-`0600`, value-free JSON manifest. No customer number, SIP trunk, external carrier, provider credential, recording, channel identifier, PBX log, or secret is retained.

The current run established that the real Asterisk ARI control plane and Stasis WebSocket event path are available, and that Asterisk can originate both disposable channels to software endpoints and place those exact channels into one actual mixing bridge in the isolated topology. This is a real PBX transport check, not an internal-loopback or mock claim about external telephony.

## Actual result

```json
{"contractVersion":"B22-PBX-CANARY/1","profile":"ASTERISK_ARI_STASIS_SYNTHETIC_SIP","transportChecks":["ARI_STASIS_WEBSOCKET_TWO_START_EVENTS","ARI_ORIGINATE_TWO_SYNTHETIC_SIP_ENDPOINTS","ARI_MIXING_BRIDGE_MEMBERSHIP","ARI_BRIDGE_ENTRY_EVENTS"],"pbxReal":"BLOCKED_RUNTIME_CONFORMANCE","lineReal":"BLOCKED_EXTERNAL","evidenceLevel":"CONTRACT_CLOSED"}
```

The manifest binds the current runner, JDK Stasis helper and Asterisk overlay through `sourceDigest`, but deliberately carries no native values—only fixed image/contract identifiers and blocked capability names. A two-channel bridge demonstrates neither RTP in both directions nor the B22-S proof that the two normalized participant legs share the expected bidirectional media relationship. It is not an enablement report and cannot be consumed as production readiness.

## Why PBX_REAL is not passed yet

The repository currently contains B22a's connector identity/control plane and CP/PC/PD/PM design oracles, but not the runtime they require. Therefore the canary cannot honestly attest any of the following:

- a fenced Stasis WebSocket owner with reconnect-gap semantics;
- durable adapter journal, command ledger, core inbox/outbox and `AMBIGUOUS` reconciliation;
- event-derived exact two-leg, bidirectional media proof;
- sensitive-envelope handling, recording range/cancellation, retention, or confirmed deletion;
- a B22-CP/B22-PC/B22-PD/B22-PM adapter/core oracle run against persisted state.

Accordingly, the PBX transport result remains `CONTRACT_CLOSED` and `PBX_REAL=BLOCKED_RUNTIME_CONFORMANCE`; B22-SPLIT must wait for the private adapter/runtime conformance implementation rather than treating a Docker loopback as a pass.

`LINE_REAL=BLOCKED_EXTERNAL` is separate and expected: no SIP trunk or cloud-telephony test account was provided. A later real-line canary must use a separately authorized external line and may not replace it with either of these internal SIPp endpoints.

## Reproduce

```bash
report=$(mktemp /private/tmp/b22-pbx-report.XXXXXX.json)
scripts/b22-pbx-canary.sh --allow-synthetic-sip-endpoints --report "$report"
jq . "$report"
```

Required local images are pinned in the script's current image digest/report profile. The test may download `mlan/asterisk` and `sieteunoseis/sipp`; it requires Docker but no paid account, external line, or production credential.
