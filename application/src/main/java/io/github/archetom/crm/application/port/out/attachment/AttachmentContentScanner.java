package io.github.archetom.crm.application.port.out.attachment;

import io.github.archetom.crm.domain.model.AttachmentContentEvidence;

public interface AttachmentContentScanner {
    ScanResult scan(AttachmentReadHandle handle, AttachmentContentEvidence evidence) throws Exception;

    /**
     * {@code definitionGeneration} is the exact generation that performed this scan, rather
     * than a later observation of engine readiness. It is mandatory for a CLEAN conclusion.
     */
    record ScanResult(ScanVerdict verdict, DefinitionBasis definitionBasis,
                      String definitionGeneration, String safeReason) {
        /** Compatibility constructor for failures/non-clean synthetic implementations. */
        public ScanResult(ScanVerdict verdict, DefinitionBasis definitionBasis, String safeReason) {
            this(verdict, definitionBasis, null, safeReason);
        }
    }
    enum ScanVerdict { NO_THREAT_DETECTED, THREAT_DETECTED, UNSUPPORTED, FAILED }
    enum DefinitionBasis { PINNED_DEFINITIONS, MANAGED_DEFINITIONS }
}
