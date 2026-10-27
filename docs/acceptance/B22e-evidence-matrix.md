# B22e layered evidence matrix acceptance

Date: 2026-07-23

`scripts/b22-conformance-matrix.sh` accepts exactly three existing, owner-only (`0600`) reports:

- the test-only CP/PC/PD/PM private durable oracle;
- the MySQL/Testcontainers core inbox conformance lane; and
- the isolated Asterisk ARI/Stasis/SIPp transport canary.

It validates each report's fixed contract/profile/evidence fields before writing a new owner-only, value-free `B22-EVIDENCE-MATRIX/1` manifest. The matrix binds the inputs only by their file digests. It does not copy a connector, endpoint, number, provider account, ARI value, media reference, secret, or native event into the output.

```bash
sh scripts/b22-conformance-matrix.sh \
  --private-report /absolute/path/b22-private.json \
  --core-report /absolute/path/b22-core.json \
  --pbx-report /absolute/path/b22-pbx.json \
  --report /absolute/path/b22-matrix.json
```

The current matrix records the transport lane as `CONTRACT_CLOSED` and the two durable lanes as `DURABLE_SIMULATED`. Its top-level `evidenceLevel` is therefore `DURABLE_SIMULATED`, while it fixes `productionReady=false`, `PBX_REAL=BLOCKED_RUNTIME_CONFORMANCE`, and `LINE_REAL=BLOCKED_EXTERNAL`. It lists the remaining private-adapter and external-line gates instead of deriving a pass from a bridge, a synthetic endpoint, or a test oracle.

The next non-external implementation gate is a private Asterisk adapter with a connector-partitioned durable journal/ledger and fenced Stasis owner, event-derived bidirectional two-leg proof, `AMBIGUOUS` reconciliation, and actual PBX sensitive/media retention and confirmed-deletion evidence. An external SIP trunk or cloud-telephony test account remains a separate, later prerequisite for `LINE_REAL` only.
