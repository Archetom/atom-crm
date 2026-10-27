# Files and attachments

Files use a provider-neutral lifecycle, target-owned authorization, quarantine inspection and bounded server-side streams. The bundled runtime combines secure local storage, bounded magic detection and a streamed ClamAV engine. Production files, scanner and workers are disabled by default. S3-like storage and managed event inspection have conformance models, but are not installed production adapters.

## Public boundary and ownership

Ordinary attachments use `/api/v1/attachment-targets/{targetType}/{targetId}/attachments`. Every operation resolves the target before file metadata or storage access. `files:read`/`files:write` intersects the target's current capability, record scope and lifecycle; it never grants tenant-wide access to an otherwise hidden target. Unknown targets and cross-tenant, mismatched, hidden or non-public files fail closed without disclosing their existence.

`AttachmentTargetType` is a closed catalog independent of customizable business-object metadata. Work Task uses current participant/manager visibility and does not inherit access from an optional CRM relationship. CRM targets use their own guards. Product has its own tenant-member capability; Return Visit keeps its record scope. OA Announcement distinguishes draft management from published recipient access. OA Request collaboration checks the authoritative approval instance and does not mutate immutable submission facts.

An intent binds one file to one `(tenant, targetType, targetId)` and a typed upload origin. A file cannot move or attach to a second target. Same-tenant parent foreign keys and one-file/one-relation uniqueness enforce this in MySQL. Intent creation, publication, withdrawal and parent deletion lock the target root before file rows; OA Request follows its approval-instance-before-request lock order.

Ordinary attachments and governed `FILE` values are distinct. `FILE` values use structured `fileFields`, dedicated binding/snapshot relations and current field policy; they never enter scalar `customFields` or arbitrary facts maps. Pre-create drafts bind only through their owning command. Public marketing uploads use a canonical form/version and an exact hash-only draft capability: the credential is returned once, does not authenticate a tenant member and grants no download access. Submission rechecks the persisted form owner's current authorization and atomically consumes the draft into the Lead.

## Storage and inspection lifecycle

Byte state, inspection state, publication state and target relation remain independent. Successful upload is not publication, and a scanner `OK` is not sufficient to mark content safe. A file becomes available only after exact storage evidence, `CLEAN` inspection and an active target relation agree in the publication transaction.

Before byte I/O, the application durably records a planned locator for the exact attempt. Storage returns an opaque immutable generation, actual byte count and canonical full-object SHA-256. Current paths/keys, ETags, modification times, client digests and provider checksum maps cannot substitute for this evidence. Allocate, store, probe, read, delete, authoritative absence and incomplete cleanup are separate port operations.

Inspection requires the same immutable generation, complete byte/integrity proof, an allowlisted identified type, current detector/scanner evidence and all byte/time limits. Positive threat or a deterministic type-policy conflict is `REJECTED`. Unknown format, protected content, timeout, limits, stale definitions, protocol drift or version mismatch is `INDETERMINATE`. Only the complete intersection is `CLEAN`.

Storage, scanner and provider paths, URLs, sockets, native findings, parser metadata and SDK objects stay in `infra/external`. Application ports expose typed evidence and closeable/cancelable streams. Container-aware parsing, if added, requires a separate constrained process; it must not turn the core JVM into an untrusted document parser.

Worker claims use durable leases and attempt fences. Storage and scanner I/O run outside MySQL transactions; stale workers cannot settle a replacement attempt. A completed scan still remains private until the owning target is reauthorized and publication commits.

## Limits and deployment readiness

The public file profile allows PDF, PNG and JPEG with a 16 MiB ceiling. Controlled CSV import uses a separate draft target and its own 1 MiB/200-row boundary. The storage-port absolute ceiling is 64 MiB and its streaming buffer ceiling is 64 KiB; these do not widen a public policy. A target has at most 100 active ordinary attachments. Native single-range read is a separate capability. Multipart, resumable upload, browser-direct/presigned transfer and transparent SDK multipart are unsupported.

`atom.files.enabled`, `atom.files.clamd.enabled` and `atom.files.workers.enabled` must be configured together with valid policies and evidence. A ready deployment needs positive tenant/global ready/quarantine quotas, pending-intent bounds, upload/download/inspection concurrency, free-capacity reserves, worker leases/deadlines and an independent deletion reserve. Missing or invalid evidence makes the affected operation unavailable.

The `LOCAL_SECURE_NIO_V1` profile requires a dedicated non-webroot encrypted volume, pre-provisioned shards, owner-only permissions, `SecureDirectoryStream`, `NOFOLLOW_LINKS`, `CREATE_NEW`, free-space floors and filesystem crash evidence. Node-local storage cannot be assumed safe for production replicas: shared visibility, fencing, failover and restore behavior require explicit proof.

`BOUNDED_MAGIC_V1` plus `STREAMING_ENGINE_V1` is the installed inspection profile. Production clamd uses a Unix-domain socket. A loopback test runner does not produce production readiness. The owner-only conformance report must match the current build, storage binding/root, policy, scanner definition generation and complete required scenarios. Readiness expires within five minutes and cannot be synthesized from configuration flags alone.

`atom.files.public-marketing.enabled` is a separate default-off gate. Public drafts also require bounded lifetime, count, bytes, network admission and cleanup readiness. A functioning private member upload does not imply that anonymous intake is enabled. See [configuration](configuration.md#file-storage) for the runtime groups and [HTTP APIs](api-reference.md) for target-specific routes.

## Read, withdrawal and deletion

Each full/range request repeats current member, target, relation and field checks before opening storage. The response uses fixed safe headers and a bounded same-origin body; no provider redirect, public URL or full-object buffering is exposed. Unsupported ranges fail rather than silently downloading a full object. Disconnects, deadlines, byte limits and retention/removal cancel the upstream handle without reopening it.

Parent soft deletion withdraws current/private links and creates durable exact-generation deletion work in the same transaction. Work Task instead rejects deletion when upload or attachment history exists. Customer/Contact pool transitions suspend attachment access and defer applicable work without inventing a new owner. OA publication and terminal approval transitions apply their own frozen-relation and private-file cleanup rules.

A delete response, read failure or ordinary not-found does not prove removal. The worker confirms authoritative absence of the exact generation, fences old attempts and drains open handles before completion. Incomplete uploads use their own abort/reconciliation path. Storage deletion, replica/backup expiry and physical destruction remain separate guarantees; backups must restore both database links and the actual private object store consistently.

## Verification

Use the [testing guide](test-guide.md) and confirm real MySQL execution for target authorization, transaction, mapper, lease and deletion changes. Relevant tests include `AttachmentFileTest`, `AttachmentRuntimePolicyTest`, `AttachmentTargetAccessRegistryTest`, `SecureLocalAttachmentStorageTest`, `AttachmentStorageConformanceTest`, `AttachmentInspectionConformanceTest`, `AttachmentPersistenceIntegrationTest` and `AttachmentComposedConformanceTest`. Target-specific integration tests cover Work, CRM, public forms and OA.

[`attachment-conformance-matrix.sh`](../scripts/attachment-conformance-matrix.sh) runs the composed checks; [`attachment-linux-canary.sh`](../scripts/attachment-linux-canary.sh) exercises the real Linux/Unix-socket lane. Read their prerequisites and report output arguments before execution. Pure models, simulated crashes, real filesystem/scanner probes and deployment readiness are separate evidence. Existing profile, scenario and report identifiers are compatibility contracts and remain stable even when documentation or runner filenames change.
