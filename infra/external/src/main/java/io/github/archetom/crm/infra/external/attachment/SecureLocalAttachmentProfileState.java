package io.github.archetom.crm.infra.external.attachment;

import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.StorageBindingRef;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

/** One immutable bootstrap decision, shared by storage and readiness beans. */
final class SecureLocalAttachmentProfileState {
    private final AttachmentRuntimePolicy policy;
    private final AttachmentStorage storage;
    private final StorageBindingRef binding;
    private final SecureLocalAttachmentConformanceManifest manifest;
    private final SecureLocalAttachmentConformanceReportSource reportSource;

    SecureLocalAttachmentProfileState(SecureLocalAttachmentProperties properties,
                                      SecureLocalAttachmentConformanceReportSource reportSource,
                                      StreamingInspectionEngine engine, Clock clock) {
        AttachmentRuntimePolicy resolvedPolicy = AttachmentRuntimePolicy.disabled();
        AttachmentStorage resolvedStorage = null;
        StorageBindingRef resolvedBinding = null;
        SecureLocalAttachmentConformanceManifest resolvedManifest = null;
        try {
            if (properties != null && properties.isEnabled() && properties.getPolicy() != null
                    && properties.getInspection() != null && reportSource != null && engine != null) {
                var p = properties.getPolicy();
                resolvedPolicy = new AttachmentRuntimePolicy(true, p.getMaxFileBytes(), p.getMaxReadyBytes(),
                        p.getMaxQuarantineBytes(), p.getMaxPendingIntents(), p.getMaxActiveUploads(),
                        p.getMaxActiveInspections(), p.getMaxActiveDownloads(), p.getGlobalReserveBytes(),
                        p.getDeletionControlReserve(), p.getIntentTtl(), p.getUploadOpenTimeout(),
                        p.getUploadIdleTimeout(), p.getUploadTotalTimeout(), p.getInspectionTotalTimeout(),
                        p.getDownloadOpenTimeout(), p.getDownloadIdleTimeout(), p.getDownloadTotalTimeout(),
                        p.getJobLease(), p.getJobHeartbeat(), p.getMaxAttempts(), p.getQuarantineRetention(),
                        p.getTombstoneRetention(), p.getClaimSize());
                resolvedBinding = new StorageBindingRef(Base64.getUrlDecoder().decode(properties.getBindingBase64()));
                resolvedManifest = reportSource.currentReport().map(SecureLocalAttachmentConformanceManifest::new)
                        .orElse(null);
                String rootDigest = SecureLocalAttachmentStorage.rootAttestationDigest(properties.getSecureLocalRoot());
                if (resolvedManifest != null && resolvedManifest.current(properties, resolvedBinding, rootDigest,
                        new SecureLocalAttachmentConformanceManifest.AttachmentRuntimePolicyView(
                                SecureLocalAttachmentConformanceManifest.policyDigest(properties)),
                        engine.evidence(), clock.instant())) {
                    resolvedStorage = new SecureLocalAttachmentStorage(properties.getSecureLocalRoot(), p.getMaxFileBytes(),
                            positiveLocalFreeFloor(p.getLocalFreeFloorBytes()));
                }
            }
        } catch (RuntimeException | java.io.IOException exception) {
            // Startup remains available; missing/drifting deployment evidence is NOT_READY, not a
            // reason to silently fall back to a different storage binding.
            resolvedPolicy = AttachmentRuntimePolicy.disabled();
            resolvedStorage = null;
            resolvedBinding = null;
            resolvedManifest = null;
        }
        this.policy = resolvedStorage == null ? AttachmentRuntimePolicy.disabled() : resolvedPolicy;
        this.storage = resolvedStorage;
        this.binding = resolvedBinding;
        this.manifest = resolvedManifest;
        this.reportSource = reportSource;
    }

    AttachmentRuntimePolicy policy() { return policy; }
    AttachmentStorage storage() { return storage; }
    StorageBindingRef binding() { return binding; }
    boolean storageReady(SecureLocalAttachmentProperties properties, StreamingInspectionEngine engine, Instant now) {
        return storageReadinessEvidence(properties, now).isPresent();
    }

    /** Storage effects are fenced by storage conformance only, never by scanner freshness. */
    Optional<StorageReadinessEvidence> storageReadinessEvidence(SecureLocalAttachmentProperties properties,
                                                                 Instant now) {
        if (storage == null || binding == null || manifest == null || properties == null || now == null) {
            return Optional.empty();
        }
        try {
            // A live report authorizes the complete storage profile. If the report source is
            // revoked or unavailable after boot, only the narrow cleanup lane stays usable from
            // the unexpired boot evidence; intake/read/replay must fail closed.
            String rootDigest = SecureLocalAttachmentStorage.rootAttestationDigest(properties.getSecureLocalRoot());
            var policyView = new SecureLocalAttachmentConformanceManifest.AttachmentRuntimePolicyView(
                    SecureLocalAttachmentConformanceManifest.policyDigest(properties));
            var current = manifest;
            boolean cleanupOnly = false;
            if (reportSource != null) {
                try {
                    var refreshed = reportSource.currentReport()
                            .map(SecureLocalAttachmentConformanceManifest::new).orElse(null);
                    if (refreshed != null && refreshed.storageCurrent(properties, binding, rootDigest, policyView, now)) {
                        current = refreshed;
                    } else cleanupOnly = true;
                } catch (RuntimeException ignored) {
                    cleanupOnly = true;
                }
            }
            if (current == null || !current.storageCurrent(properties, binding, rootDigest, policyView, now)) {
                return Optional.empty();
            }
            Instant expiresAt = earliest(now.plus(Duration.ofMinutes(5)), current.report().expiresAt());
            Set<io.github.archetom.crm.application.port.out.attachment.AttachmentOperation> operations = cleanupOnly
                    ? Set.of(io.github.archetom.crm.application.port.out.attachment.AttachmentOperation.PROBE_EXACT,
                    io.github.archetom.crm.application.port.out.attachment.AttachmentOperation.ABORT_INCOMPLETE,
                    io.github.archetom.crm.application.port.out.attachment.AttachmentOperation.DELETE_EXACT,
                    io.github.archetom.crm.application.port.out.attachment.AttachmentOperation.CONFIRM_ABSENCE)
                    : Set.of(io.github.archetom.crm.application.port.out.attachment.AttachmentOperation.values());
            return expiresAt.isAfter(now) ? Optional.of(new StorageReadinessEvidence(expiresAt, operations)) : Optional.empty();
        } catch (RuntimeException | java.io.IOException exception) {
            return Optional.empty();
        }
    }

    /**
     * Revalidates the independent report and live engine on every readiness observation. Any
     * adapter/report fault is scoped to B24 and fails closed instead of aborting normal CRM boot.
     */
    Optional<ReadinessEvidence> readinessEvidence(SecureLocalAttachmentProperties properties,
                                                  StreamingInspectionEngine engine, Instant now) {
        if (storage == null || binding == null || manifest == null || properties == null || engine == null
                || reportSource == null || now == null) return Optional.empty();
        try {
            var current = reportSource.currentReport()
                    .map(SecureLocalAttachmentConformanceManifest::new).orElse(null);
            if (current == null) return Optional.empty();
            var engineEvidence = engine.evidence();
            String rootDigest = SecureLocalAttachmentStorage.rootAttestationDigest(properties.getSecureLocalRoot());
            if (!current.current(properties, binding, rootDigest,
                    new SecureLocalAttachmentConformanceManifest.AttachmentRuntimePolicyView(
                            SecureLocalAttachmentConformanceManifest.policyDigest(properties)), engineEvidence, now)) {
                return Optional.empty();
            }
            Instant expiresAt = earliest(now.plus(Duration.ofMinutes(5)), current.report().expiresAt(),
                    current.report().definitionsValidUntil(), engineEvidence.definitionsValidUntil(),
                    engineEvidence.loadedAt().plus(properties.getInspection().getDefinitionMaxAge()));
            return expiresAt.isAfter(now)
                    ? Optional.of(new ReadinessEvidence(expiresAt, engineEvidence.definitionGeneration()))
                    : Optional.empty();
        } catch (RuntimeException | java.io.IOException exception) {
            return Optional.empty();
        }
    }

    private static Instant earliest(Instant... instants) {
        Instant earliest = null;
        for (Instant instant : instants) {
            if (instant == null) throw new IllegalArgumentException("attachment readiness expiry is missing");
            if (earliest == null || instant.isBefore(earliest)) earliest = instant;
        }
        return earliest;
    }

    private static long positiveLocalFreeFloor(Long value) {
        if (value == null || value < 1) throw new IllegalArgumentException("secure local free floor is incomplete");
        return value;
    }

    record ReadinessEvidence(Instant expiresAt, String definitionGeneration) { }
    record StorageReadinessEvidence(Instant expiresAt,
                                    Set<io.github.archetom.crm.application.port.out.attachment.AttachmentOperation> operations) { }
}
