package io.github.archetom.crm.infra.external.attachment;

import io.github.archetom.crm.application.port.out.attachment.AttachmentContentScanner;
import io.github.archetom.crm.application.port.out.attachment.AttachmentReadHandle;
import io.github.archetom.crm.domain.model.AttachmentContentEvidence;

import java.io.InputStream;

/** Delegates one exact quarantined stream to a private, fenced streaming inspection engine. */
public final class StreamedInspectionAdapter implements AttachmentContentScanner {
    private final StreamingInspectionEngine engine;
    public StreamedInspectionAdapter(StreamingInspectionEngine engine) { this.engine = engine; }
    @Override public ScanResult scan(AttachmentReadHandle handle, AttachmentContentEvidence evidence) throws Exception {
        // Fence both sides of execution. A same-basis reload (A -> B) is still a different
        // scanner generation and may never normalize an A result as a B CLEAN result.
        StreamingInspectionEngine.Evidence before = engine.evidence();
        try (InputStream input = handle.inputStream()) {
            ScanResult result = engine.scan(input, evidence);
            // A positive detection is the strongest observation. Freshness/generation drift may
            // prevent a CLEAN conclusion, but must never erase an already determined threat.
            // It still has to identify the generation/basis that actually began this scan.
            if (result != null && result.verdict() == ScanVerdict.THREAT_DETECTED) {
                if (before != null && before.definitionBasis() != null && before.definitionGeneration() != null
                        && result.definitionBasis() == before.definitionBasis()
                        && before.definitionGeneration().equals(result.definitionGeneration())) {
                    return result;
                }
                return new ScanResult(ScanVerdict.FAILED, null, null, "THREAT_EVIDENCE_MISMATCH");
            }
            StreamingInspectionEngine.Evidence after = engine.evidence();
            if (result == null || before == null || after == null || before.definitionBasis() == null
                    || after.definitionBasis() == null || before.definitionGeneration() == null
                    || after.definitionGeneration() == null || result.definitionGeneration() == null
                    || result.definitionBasis() != before.definitionBasis()
                    || result.definitionBasis() != after.definitionBasis()
                    || !result.definitionGeneration().equals(before.definitionGeneration())
                    || !result.definitionGeneration().equals(after.definitionGeneration())) {
                return new ScanResult(ScanVerdict.FAILED, null, null, "ENGINE_EVIDENCE_MISMATCH");
            }
            return result;
        }
    }
}
