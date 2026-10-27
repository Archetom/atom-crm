package io.github.archetom.crm.infra.external.attachment;

import io.github.archetom.crm.application.port.out.attachment.AttachmentContentScanner;
import io.github.archetom.crm.application.port.out.attachment.StorageBindingRef;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

/** Verifies externally produced, current-build B24 evidence; it never reads a self-attested property. */
final class SecureLocalAttachmentConformanceManifest {
    static final String PROFILE = "LOCAL_SECURE_NIO_V1+BOUNDED_MAGIC_V1+STREAMING_ENGINE_V1";
    static final String CONTRACT = "B24A_CONFORMANCE_V1";
    private static final Set<String> REQUIRED_SCENARIOS = Set.of(
            "S-01", "S-02", "S-03", "S-04", "S-05", "S-06", "S-07", "S-08", "S-09", "S-10", "S-11", "S-12", "S-13", "S-14",
            "I-01", "I-02", "I-03", "I-04", "I-05", "I-06", "I-07", "I-08", "I-09", "I-10",
            "I-11", "I-12", "I-13", "I-14",
            "X-01", "X-02", "X-03", "X-04", "X-05", "X-06", "X-07", "X-08");
    private final SecureLocalAttachmentConformanceReportSource.Report report;

    SecureLocalAttachmentConformanceManifest(SecureLocalAttachmentConformanceReportSource.Report report) {
        this.report = report;
    }

    boolean current(SecureLocalAttachmentProperties properties, StorageBindingRef binding, String storageRootDigest,
                    AttachmentRuntimePolicyView policy, StreamingInspectionEngine.Evidence engine, Instant now) {
        if (!storageCurrent(properties, binding, storageRootDigest, policy, now)
                || engine == null || properties.getInspection() == null
                || !"BOUNDED_MAGIC_V1".equals(properties.getInspection().getDetectorProfile())
                || !"STREAMING_ENGINE_V1".equals(properties.getInspection().getScannerProfile())
                || !same(properties.getInspection().getDetectorProfile(), report.detectorProfile())
                || !same(properties.getInspection().getScannerProfile(), report.scannerProfile())
                || !same(properties.getInspection().getDetectorCatalogRevision(), report.detectorCatalogRevision())
                || !same(properties.getInspection().getAllowlistRevision(), report.allowlistRevision())
                || !same(engine.engineBuildDigest(), report.engineBuildDigest())
                || !same(engine.definitionGeneration(), report.definitionGeneration())
                || engine.definitionBasis() == null || engine.definitionBasis() != report.definitionBasis()
                || engine.loadedAt() == null || engine.definitionsValidUntil() == null
                || report.definitionsValidUntil() == null || report.expiresAt() == null
                || !engine.definitionsValidUntil().isAfter(now) || !report.definitionsValidUntil().isAfter(now)
                || !report.expiresAt().isAfter(now) || !withinDefinitionAge(engine.loadedAt(),
                properties.getInspection().getDefinitionMaxAge(), now)
                || !report.passedScenarios().containsAll(REQUIRED_SCENARIOS)) {
            return false;
        }
        return engine.definitionBasis() == AttachmentContentScanner.DefinitionBasis.PINNED_DEFINITIONS
                || engine.definitionBasis() == AttachmentContentScanner.DefinitionBasis.MANAGED_DEFINITIONS;
    }

    /** Storage evidence deliberately excludes live scanner/definition state. */
    boolean storageCurrent(SecureLocalAttachmentProperties properties, StorageBindingRef binding, String storageRootDigest,
                           AttachmentRuntimePolicyView policy, Instant now) {
        return properties != null && binding != null && storageRootDigest != null && policy != null && now != null && report != null
                && PROFILE.equals(properties.getStorageProfile())
                && CONTRACT.equals(report.contractVersion())
                && same(runtimeAdapterDigest(), report.adapterBuildDigest())
                && same(expectedDigest(binding), report.bindingDigest())
                && same(storageRootDigest, report.storageRootDigest())
                && same(policy.digest(), report.policyDigest())
                && report.expiresAt() != null && report.expiresAt().isAfter(now)
                && report.passedScenarios().containsAll(REQUIRED_SCENARIOS);
    }

    static String expectedDigest(StorageBindingRef binding) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(binding.copyValue()));
        } catch (Exception exception) { throw new IllegalStateException("attachment binding digest unavailable", exception); }
    }
    static Set<String> requiredScenarios() { return REQUIRED_SCENARIOS; }

    SecureLocalAttachmentConformanceReportSource.Report report() { return report; }

    /**
     * Computed once per process. The digest covers the loaded code sources, which cannot change
     * while the process runs, but computing it reads and hashes every entry of each artifact —
     * once per readiness check, which happens on every attachment operation.
     */
    private static volatile String runtimeAdapterDigest;

    static String runtimeAdapterDigest() {
        String cached = runtimeAdapterDigest;
        if (cached != null) {
            return cached;
        }
        synchronized (SecureLocalAttachmentConformanceManifest.class) {
            if (runtimeAdapterDigest == null) {
                runtimeAdapterDigest = computeRuntimeAdapterDigest();
            }
            return runtimeAdapterDigest;
        }
    }

    private static String computeRuntimeAdapterDigest() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Hash complete runtime code sources rather than a hand-maintained class list. This
            // conservatively invalidates an old report for *any* B24 runtime change, including
            // transport, authorization and the start-module scheduler/composition. A missing
            // production artifact is NOT_READY rather than an attestable placeholder.
            updateCodeSource(digest, "external", SecureLocalAttachmentStorage.class);
            updateCodeSource(digest, "application", requiredRuntimeClass(
                    "io.github.archetom.crm.application.service.impl.AttachmentTransferServiceImpl"));
            updateCodeSource(digest, "domain", requiredRuntimeClass(
                    "io.github.archetom.crm.domain.entity.AttachmentFile"));
            updateCodeSource(digest, "persistence", requiredRuntimeClass(
                    "io.github.archetom.crm.infra.persistence.repository.AttachmentWorkStoreRepositoryImpl"));
            updateCodeSource(digest, "api", requiredRuntimeClass(
                    "io.github.archetom.crm.api.facade.AttachmentFacade"));
            updateCodeSource(digest, "facade", requiredRuntimeClass(
                    "io.github.archetom.crm.infra.facade.AttachmentFacadeImpl"));
            updateCodeSource(digest, "rest", requiredRuntimeClass(
                    "io.github.archetom.crm.infra.rest.controller.AttachmentController"));
            updateCodeSource(digest, "rest-security", requiredRuntimeClass(
                    "io.github.archetom.crm.infra.rest.config.SecurityConfig"));
            updateCodeSource(digest, "start", requiredRuntimeClass(
                    "io.github.archetom.crm.BackgroundWorkScheduler"));
            updateCodeSource(digest, "shared", requiredRuntimeClass(
                    "io.github.archetom.crm.shared.enums.ApplicationErrorCode"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("attachment adapter build digest unavailable", exception);
        }
    }

    private static Class<?> requiredRuntimeClass(String className) throws ClassNotFoundException {
        try {
            return Class.forName(className, false, SecureLocalAttachmentConformanceManifest.class.getClassLoader());
        } catch (LinkageError exception) {
            throw new IllegalStateException("attachment runtime class is unavailable: " + className, exception);
        }
    }
    private static void updateCodeSource(MessageDigest digest, String label, Class<?> anchor) throws Exception {
        if (anchor.getProtectionDomain() == null || anchor.getProtectionDomain().getCodeSource() == null
                || anchor.getProtectionDomain().getCodeSource().getLocation() == null) {
            throw new IllegalStateException("attachment runtime code source is unavailable: " + label);
        }
        Path source = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
        digest.update(("SOURCE:" + label + "\n").getBytes(StandardCharsets.UTF_8));
        if (Files.isDirectory(source)) {
            try (var stream = Files.walk(source)) {
                List<Path> entries = stream.filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(path -> source.relativize(path).toString())).toList();
                for (Path entry : entries) {
                    digest.update((source.relativize(entry).toString() + "\n").getBytes(StandardCharsets.UTF_8));
                    digest.update(Files.readAllBytes(entry));
                }
            }
            return;
        }
        try (JarFile jar = new JarFile(source.toFile())) {
            List<java.util.jar.JarEntry> entries = jar.stream().filter(entry -> !entry.isDirectory())
                    .sorted(Comparator.comparing(java.util.jar.JarEntry::getName)).toList();
            for (var entry : entries) {
                digest.update((entry.getName() + "\n").getBytes(StandardCharsets.UTF_8));
                try (var input = jar.getInputStream(entry)) { digest.update(input.readAllBytes()); }
            }
        }
    }

    static String policyDigest(SecureLocalAttachmentProperties properties) {
        try {
            var policy = properties.getPolicy();
            var inspection = properties.getInspection();
            String canonical = String.join("|", PROFILE, String.valueOf(policy.getMaxFileBytes()),
                    String.valueOf(policy.getMaxReadyBytes()), String.valueOf(policy.getMaxQuarantineBytes()),
                    String.valueOf(policy.getMaxPendingIntents()), String.valueOf(policy.getMaxActiveUploads()),
                    String.valueOf(policy.getMaxActiveInspections()), String.valueOf(policy.getMaxActiveDownloads()),
                    String.valueOf(policy.getGlobalReserveBytes()), String.valueOf(policy.getDeletionControlReserve()),
                    String.valueOf(policy.getLocalFreeFloorBytes()),
                    String.valueOf(policy.getIntentTtl()), String.valueOf(policy.getUploadOpenTimeout()),
                    String.valueOf(policy.getUploadIdleTimeout()), String.valueOf(policy.getUploadTotalTimeout()),
                    String.valueOf(policy.getInspectionTotalTimeout()), String.valueOf(policy.getDownloadOpenTimeout()),
                    String.valueOf(policy.getDownloadIdleTimeout()), String.valueOf(policy.getDownloadTotalTimeout()),
                    String.valueOf(policy.getJobLease()), String.valueOf(policy.getJobHeartbeat()),
                    String.valueOf(policy.getMaxAttempts()), String.valueOf(policy.getQuarantineRetention()),
                    String.valueOf(policy.getTombstoneRetention()), String.valueOf(policy.getClaimSize()),
                    String.valueOf(inspection.getDetectorProfile()), String.valueOf(inspection.getScannerProfile()),
                    String.valueOf(inspection.getDetectorCatalogRevision()), String.valueOf(inspection.getAllowlistRevision()),
                    String.valueOf(inspection.getDefinitionMaxAge()));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("attachment policy digest unavailable", exception); }
    }

    private static boolean withinDefinitionAge(Instant loadedAt, Duration maxAge, Instant now) {
        return maxAge != null && !maxAge.isNegative() && !maxAge.isZero()
                && !loadedAt.isAfter(now) && loadedAt.plus(maxAge).isAfter(now);
    }
    private static boolean same(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    /** Limits the verifier's dependency surface to a digest, not mutable policy objects. */
    record AttachmentRuntimePolicyView(String digest) { }
}
