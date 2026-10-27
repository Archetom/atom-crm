# B22d private-adapter durable-oracle acceptance

Date: 2026-07-23

`B22PrivateAdapterDurabilityConformanceTest` is a private, test-only durable-state oracle for the B22 CP/PC/PD/PM contracts. It uses only fixed synthetic strings, fixed digests and encrypted synthetic bytes. It has no ARI/SIP HTTP endpoint, connector route, provider account, telephone number, recording reference, media URL, production bean or public ingress.

The oracle persists then reconstructs its state around four failure-critical paths: a fenced reconnect rejects the stale owner while preserving a journal and exact distinct-leg proof; a command ledger changes an interrupted `DISPATCHING` command to `AMBIGUOUS` without a second provider start; connector-scoped AES-GCM and HMAC prevent cross-connector reads and retention removes an expired payload; and a single native byte range rejects invalid input without opening a stream, honors cancellation, and reports removal only after an exact underlying absence check.

Verification:

```bash
sh ./mvnw -q -pl infra/external -am test \
  -Dtest=B22PrivateAdapterDurabilityConformanceTest -Dsurefire.failIfNoSpecifiedTests=false

tmp_report=$(mktemp /private/tmp/b22-private-adapter.XXXXXX)
sh scripts/b22-private-adapter-durable-conformance.sh --report "$tmp_report"
```

The runner emits a mode-`0600`, value-free `B22-PRIVATE-ADAPTER-DURABLE/1` manifest. It records `DURABLE_SIMULATED`, `PBX_REAL=BLOCKED_RUNTIME_CONFORMANCE`, `LINE_REAL=BLOCKED_EXTERNAL`, and `productionReady=false`. The report contains only a source digest, fixed check names, and no provider or customer value.

This is not an Asterisk adapter, not a Stasis event stream, not a bidirectional RTP proof, and not a real recording-system exercise. It cannot satisfy real PBX conformance or B22-SPLIT. `PBX_REAL` requires the same CP/PC/PD/PM checkpoints against a private adapter connected to real Asterisk software, including PBX sink leak scanning and actual recording deletion. `LINE_REAL` separately requires an authorized SIP trunk or cloud-telephony account; internal SIPp loopback is never a substitute.
