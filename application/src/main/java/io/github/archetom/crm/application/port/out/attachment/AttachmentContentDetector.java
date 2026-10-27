package io.github.archetom.crm.application.port.out.attachment;

import io.github.archetom.crm.domain.model.AttachmentContentEvidence;

public interface AttachmentContentDetector {
    Detection detect(AttachmentReadHandle handle, AttachmentContentEvidence evidence) throws Exception;

    record Detection(DetectionState state, String mediaType, EvidenceLevel evidenceLevel,
                     String safeReason) { }
    enum DetectionState { IDENTIFIED, UNSUPPORTED, INDETERMINATE }
    enum EvidenceLevel { BOUNDED_MAGIC, ISOLATED_CONTAINER }
}
