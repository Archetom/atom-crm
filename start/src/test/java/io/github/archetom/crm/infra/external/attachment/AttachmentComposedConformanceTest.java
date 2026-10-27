package io.github.archetom.crm.infra.external.attachment;

import io.github.archetom.crm.application.port.out.attachment.AttachmentContentScanner;
import io.github.archetom.crm.application.port.out.attachment.AttachmentOperation;
import io.github.archetom.crm.application.port.out.attachment.StorageBindingRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** X conformance executes only in the complete start-module runtime. */
class AttachmentComposedConformanceTest {
    @TempDir Path root;

    @Test
    void currentManifestEnablesTheCompleteSecureLocalRuntime() {
        byte[] rawBinding = new byte[] {11, 12, 13};
        SecureLocalAttachmentProperties properties = complete(rawBinding);
        StreamingInspectionEngine engine = engine();
        SecureLocalAttachmentConformanceReportSource reports = () -> Optional.of(report(properties, rawBinding,
                engine.evidence(), Instant.now().plusSeconds(60)));
        SecureLocalAttachmentProfileState state = new SecureLocalAttachmentProfileState(properties, reports, engine,
                Clock.systemUTC());
        assertTrue(state.policy().enabled());
        assertTrue(state.storageReady(properties, engine, Instant.now()));
        assertTrue(state.storage() instanceof SecureLocalAttachmentStorage);
    }

    @Test
    void propertiesAloneCanNeverSelfAttestReadiness() {
        SecureLocalAttachmentProfileState state = new SecureLocalAttachmentProfileState(
                complete(new byte[] {21, 22, 23}), null, engine(), Clock.systemUTC());
        assertFalse(state.policy().enabled());
        assertTrue(state.storage() == null);
    }

    @Test
    void missingLocalFreeFloorKeepsEvenACompleteManifestNotReady() {
        byte[] rawBinding = new byte[] {24, 25, 26};
        SecureLocalAttachmentProperties properties = complete(rawBinding);
        properties.getPolicy().setLocalFreeFloorBytes(null);
        StreamingInspectionEngine engine = engine();
        SecureLocalAttachmentConformanceReportSource reports = () -> Optional.of(report(properties, rawBinding,
                engine.evidence(), Instant.now().plusSeconds(60)));
        SecureLocalAttachmentProfileState state = new SecureLocalAttachmentProfileState(properties, reports, engine,
                Clock.systemUTC());
        assertFalse(state.policy().enabled());
        assertTrue(state.storage() == null);
    }

    @Test
    void reportFaultKeepsOnlyTheCleanupLane() {
        Instant now = Instant.parse("2026-07-20T00:00:00Z");
        byte[] rawBinding = new byte[] {31, 32, 33};
        SecureLocalAttachmentProperties properties = complete(rawBinding);
        StreamingInspectionEngine engine = fixedEngine(now);
        AtomicBoolean failAtRuntime = new AtomicBoolean();
        SecureLocalAttachmentConformanceReportSource reports = () -> {
            if (failAtRuntime.get()) throw new IllegalStateException("report transport unavailable");
            return Optional.of(report(properties, rawBinding, engine.evidence(), now.plusSeconds(60)));
        };
        SecureLocalAttachmentProfileState state = new SecureLocalAttachmentProfileState(properties, reports, engine,
                Clock.fixed(now, java.time.ZoneOffset.UTC));
        assertTrue(state.policy().enabled());
        assertEquals(now.plusSeconds(20), state.readinessEvidence(properties, engine, now).orElseThrow().expiresAt());
        failAtRuntime.set(true);
        assertTrue(state.storageReadinessEvidence(properties, now).orElseThrow().operations()
                .contains(AttachmentOperation.DELETE_EXACT));
        assertFalse(state.storageReadinessEvidence(properties, now).orElseThrow().operations()
                .contains(AttachmentOperation.READ_FULL));
        assertFalse(state.readinessEvidence(properties, engine, now).isPresent());
        SecureLocalAttachmentProfileState broken = assertDoesNotThrow(() -> new SecureLocalAttachmentProfileState(properties,
                () -> { throw new IllegalStateException("report unavailable"); }, engine, Clock.fixed(now, java.time.ZoneOffset.UTC)));
        assertFalse(broken.policy().enabled());
    }

    private SecureLocalAttachmentConformanceReportSource.Report report(SecureLocalAttachmentProperties properties,
            byte[] binding, StreamingInspectionEngine.Evidence evidence, Instant expiresAt) {
        return new SecureLocalAttachmentConformanceReportSource.Report(
                SecureLocalAttachmentConformanceContract.contractVersion(),
                SecureLocalAttachmentConformanceContract.currentAdapterBuildDigest(), evidence.engineBuildDigest(),
                SecureLocalAttachmentConformanceContract.bindingDigest(new StorageBindingRef(binding)),
                rootDigest(), SecureLocalAttachmentConformanceContract.policyDigest(properties), "BOUNDED_MAGIC_V1",
                "magic-catalog-r1", "STREAMING_ENGINE_V1", "allowlist-r1", evidence.definitionGeneration(),
                evidence.definitionBasis(), evidence.definitionsValidUntil(), expiresAt,
                SecureLocalAttachmentConformanceContract.requiredScenarios());
    }

    private SecureLocalAttachmentProperties complete(byte[] binding) {
        SecureLocalAttachmentProperties properties = new SecureLocalAttachmentProperties();
        properties.setEnabled(true); properties.setStorageProfile(SecureLocalAttachmentConformanceContract.profile());
        properties.setSecureLocalRoot(root); properties.setBindingBase64(Base64.getUrlEncoder().withoutPadding().encodeToString(binding));
        properties.getInspection().setDetectorProfile("BOUNDED_MAGIC_V1");
        properties.getInspection().setScannerProfile("STREAMING_ENGINE_V1");
        properties.getInspection().setDetectorCatalogRevision("magic-catalog-r1");
        properties.getInspection().setAllowlistRevision("allowlist-r1");
        properties.getInspection().setDefinitionMaxAge(Duration.ofSeconds(30));
        var p = properties.getPolicy();
        p.setMaxFileBytes(1024L); p.setMaxReadyBytes(4096L); p.setMaxQuarantineBytes(4096L); p.setMaxPendingIntents(10L);
        p.setMaxActiveUploads(2); p.setMaxActiveInspections(2); p.setMaxActiveDownloads(2); p.setGlobalReserveBytes(8192L); p.setDeletionControlReserve(1L); p.setLocalFreeFloorBytes(1L);
        p.setIntentTtl(Duration.ofSeconds(10)); p.setUploadOpenTimeout(Duration.ofSeconds(1)); p.setUploadIdleTimeout(Duration.ofSeconds(1)); p.setUploadTotalTimeout(Duration.ofSeconds(2));
        p.setInspectionTotalTimeout(Duration.ofSeconds(2)); p.setDownloadOpenTimeout(Duration.ofSeconds(1)); p.setDownloadIdleTimeout(Duration.ofSeconds(1)); p.setDownloadTotalTimeout(Duration.ofSeconds(2));
        p.setJobLease(Duration.ofSeconds(2)); p.setJobHeartbeat(Duration.ofSeconds(1)); p.setMaxAttempts(2); p.setQuarantineRetention(Duration.ofSeconds(2)); p.setTombstoneRetention(Duration.ofSeconds(4)); p.setClaimSize(1);
        return properties;
    }

    private static StreamingInspectionEngine engine() { return fixedEngine(Instant.now()); }
    private static StreamingInspectionEngine fixedEngine(Instant now) {
        return new StreamingInspectionEngine() {
            @Override public Evidence evidence() { return new Evidence("engine-build-r1", "definitions-r1",
                    AttachmentContentScanner.DefinitionBasis.PINNED_DEFINITIONS, now.minusSeconds(10), now.plusSeconds(60)); }
            @Override public AttachmentContentScanner.ScanResult scan(java.io.InputStream input,
                    io.github.archetom.crm.domain.model.AttachmentContentEvidence evidence) {
                return new AttachmentContentScanner.ScanResult(AttachmentContentScanner.ScanVerdict.NO_THREAT_DETECTED,
                        AttachmentContentScanner.DefinitionBasis.PINNED_DEFINITIONS, "definitions-r1", null);
            }
        };
    }
    private String rootDigest() {
        try { return SecureLocalAttachmentConformanceContract.storageRootDigest(root); }
        catch (java.io.IOException exception) { throw new AssertionError(exception); }
    }
}
