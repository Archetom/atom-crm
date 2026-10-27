package io.github.archetom.crm.infra.external.attachment;

import io.github.archetom.crm.application.port.out.attachment.AttachmentContentDetector;
import io.github.archetom.crm.application.port.out.attachment.AttachmentContentScanner;
import io.github.archetom.crm.application.port.out.attachment.AttachmentReadHandle;
import io.github.archetom.crm.domain.model.AttachmentContentEvidence;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** I-01/I-06: bounded media evidence and definition-fenced streamed scanner evidence. */
class AttachmentInspectionConformanceTest {
    @Test
    void boundedMagicAndStreamedEnginePreserveExactEvidence() throws Exception {
        byte[] bytes = "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        AttachmentContentEvidence evidence = new AttachmentContentEvidence(bytes.length,
                MessageDigest.getInstance("SHA-256").digest(bytes), 1);
        AttachmentContentDetector.Detection detection = new BoundedMagicAttachmentContentDetector()
                .detect(handle(bytes), evidence);
        assertEquals(AttachmentContentDetector.DetectionState.IDENTIFIED, detection.state());
        assertEquals("application/pdf", detection.mediaType());
        assertEquals(AttachmentContentDetector.EvidenceLevel.BOUNDED_MAGIC, detection.evidenceLevel());
        StreamingInspectionEngine engine = new StreamingInspectionEngine() {
            @Override public Evidence evidence() {
                return new Evidence("engine-build", "definitions-42",
                        AttachmentContentScanner.DefinitionBasis.PINNED_DEFINITIONS,
                        Instant.now().minusSeconds(1), Instant.now().plusSeconds(60));
            }
            @Override public AttachmentContentScanner.ScanResult scan(InputStream input, AttachmentContentEvidence supplied)
                    throws Exception {
                assertEquals(evidence, supplied);
                assertEquals(bytes.length, input.readAllBytes().length);
                return new AttachmentContentScanner.ScanResult(AttachmentContentScanner.ScanVerdict.NO_THREAT_DETECTED,
                        AttachmentContentScanner.DefinitionBasis.PINNED_DEFINITIONS, "definitions-42", null);
            }
        };
        AttachmentContentScanner.ScanResult scan = new StreamedInspectionAdapter(engine).scan(handle(bytes), evidence);
        assertEquals(AttachmentContentScanner.ScanVerdict.NO_THREAT_DETECTED, scan.verdict());
        assertEquals(AttachmentContentScanner.DefinitionBasis.PINNED_DEFINITIONS, scan.definitionBasis());
    }

    @Test
    void boundedMagicFillsItsPrefixAcrossLegalShortReads() throws Exception {
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2};
        AttachmentContentEvidence evidence = new AttachmentContentEvidence(png.length,
                MessageDigest.getInstance("SHA-256").digest(png), 1);
        InputStream shortReads = new ByteArrayInputStream(png) {
            @Override public synchronized int read(byte[] bytes, int offset, int length) {
                return super.read(bytes, offset, Math.min(1, length));
            }
        };
        AttachmentContentDetector.Detection detection = new BoundedMagicAttachmentContentDetector()
                .detect(handle(shortReads), evidence);
        assertEquals(AttachmentContentDetector.DetectionState.IDENTIFIED, detection.state());
        assertEquals("image/png", detection.mediaType());
    }

    @Test
    void scannerCannotClaimDifferentDefinitionBasisThanItsFencedEngineGeneration() throws Exception {
        StreamingInspectionEngine engine = new StreamingInspectionEngine() {
            @Override public Evidence evidence() {
                return new Evidence("engine-build", "definitions-42",
                        AttachmentContentScanner.DefinitionBasis.PINNED_DEFINITIONS,
                        Instant.now().minusSeconds(1), Instant.now().plusSeconds(60));
            }
            @Override public AttachmentContentScanner.ScanResult scan(InputStream input, AttachmentContentEvidence supplied) {
                return new AttachmentContentScanner.ScanResult(AttachmentContentScanner.ScanVerdict.NO_THREAT_DETECTED,
                        AttachmentContentScanner.DefinitionBasis.MANAGED_DEFINITIONS, "definitions-42", null);
            }
        };
        AttachmentContentScanner.ScanResult result = new StreamedInspectionAdapter(engine).scan(handle(new byte[] {1}),
                new AttachmentContentEvidence(1, new byte[32], 1));
        assertEquals(AttachmentContentScanner.ScanVerdict.FAILED, result.verdict());
    }

    @Test
    void scannerGenerationMustRemainStableForTheActualStreamedAttempt() throws Exception {
        AtomicBoolean scanned = new AtomicBoolean();
        StreamingInspectionEngine engine = new StreamingInspectionEngine() {
            @Override public Evidence evidence() {
                String generation = scanned.get() ? "definitions-b" : "definitions-a";
                return new Evidence("engine-build", generation,
                        AttachmentContentScanner.DefinitionBasis.PINNED_DEFINITIONS,
                        Instant.now().minusSeconds(1), Instant.now().plusSeconds(60));
            }
            @Override public AttachmentContentScanner.ScanResult scan(InputStream input, AttachmentContentEvidence supplied) {
                scanned.set(true);
                return new AttachmentContentScanner.ScanResult(AttachmentContentScanner.ScanVerdict.NO_THREAT_DETECTED,
                        AttachmentContentScanner.DefinitionBasis.PINNED_DEFINITIONS, "definitions-a", null);
            }
        };
        AttachmentContentScanner.ScanResult result = new StreamedInspectionAdapter(engine).scan(handle(new byte[] {1}),
                new AttachmentContentEvidence(1, new byte[32], 1));
        assertEquals(AttachmentContentScanner.ScanVerdict.FAILED, result.verdict());
    }

    @Test
    void determinedThreatSurvivesAConcurrentDefinitionGenerationReload() throws Exception {
        AtomicBoolean scanned = new AtomicBoolean();
        StreamingInspectionEngine engine = new StreamingInspectionEngine() {
            @Override public Evidence evidence() {
                return new Evidence("engine-build", scanned.get() ? "definitions-b" : "definitions-a",
                        AttachmentContentScanner.DefinitionBasis.PINNED_DEFINITIONS,
                        Instant.now().minusSeconds(1), Instant.now().plusSeconds(60));
            }
            @Override public AttachmentContentScanner.ScanResult scan(InputStream input, AttachmentContentEvidence supplied) {
                scanned.set(true);
                return new AttachmentContentScanner.ScanResult(AttachmentContentScanner.ScanVerdict.THREAT_DETECTED,
                        AttachmentContentScanner.DefinitionBasis.PINNED_DEFINITIONS, "definitions-a", null);
            }
        };
        AttachmentContentScanner.ScanResult result = new StreamedInspectionAdapter(engine).scan(handle(new byte[] {1}),
                new AttachmentContentEvidence(1, new byte[32], 1));
        assertEquals(AttachmentContentScanner.ScanVerdict.THREAT_DETECTED, result.verdict());
    }

    @Test
    void threatMustStillNameTheGenerationAndBasisThatStartedTheScan() throws Exception {
        StreamingInspectionEngine engine = new StreamingInspectionEngine() {
            @Override public Evidence evidence() {
                return new Evidence("engine-build", "definitions-a",
                        AttachmentContentScanner.DefinitionBasis.PINNED_DEFINITIONS,
                        Instant.now().minusSeconds(1), Instant.now().plusSeconds(60));
            }
            @Override public AttachmentContentScanner.ScanResult scan(InputStream input, AttachmentContentEvidence supplied) {
                return new AttachmentContentScanner.ScanResult(AttachmentContentScanner.ScanVerdict.THREAT_DETECTED,
                        AttachmentContentScanner.DefinitionBasis.MANAGED_DEFINITIONS, "definitions-b", null);
            }
        };
        AttachmentContentScanner.ScanResult result = new StreamedInspectionAdapter(engine).scan(handle(new byte[] {1}),
                new AttachmentContentEvidence(1, new byte[32], 1));
        assertEquals(AttachmentContentScanner.ScanVerdict.FAILED, result.verdict());
    }

    private static AttachmentReadHandle handle(byte[] bytes) {
        return handle(new ByteArrayInputStream(bytes));
    }
    private static AttachmentReadHandle handle(InputStream input) {
        return new AttachmentReadHandle() {
            private final InputStream value = input;
            @Override public InputStream inputStream() { return value; }
            @Override public void cancel() { }
            @Override public void close() throws java.io.IOException { value.close(); }
        };
    }
}
