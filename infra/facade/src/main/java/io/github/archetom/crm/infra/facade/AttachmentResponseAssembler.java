package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.dto.response.AttachmentResponse;
import io.github.archetom.crm.api.dto.response.AttachmentUploadIntentResponse;
import io.github.archetom.crm.api.dto.response.AttachmentUploadStatusResponse;
import io.github.archetom.crm.application.vo.AttachmentUploadIntentVO;
import io.github.archetom.crm.application.vo.AttachmentUploadStatusVO;
import io.github.archetom.crm.application.vo.AttachmentVO;

final class AttachmentResponseAssembler {
    private AttachmentResponseAssembler() { }
    static AttachmentUploadIntentResponse toResponse(AttachmentUploadIntentVO value) {
        return new AttachmentUploadIntentResponse().setDraftId(value.draftId())
                .setFileId(value.fileId()).setDisplayName(value.displayName())
                .setDeclaredMediaType(value.declaredMediaType()).setExpectedBytes(value.expectedBytes())
                .setStatus(value.status()).setVersion(value.version()).setExpiresAt(value.expiresAt());
    }
    static AttachmentUploadStatusResponse toResponse(AttachmentUploadStatusVO value) {
        return new AttachmentUploadStatusResponse().setStatus(value.status())
                .setFailureCategory(value.failureCategory()).setVersion(value.version());
    }
    static AttachmentResponse toResponse(AttachmentVO value) {
        return new AttachmentResponse().setFileId(value.fileId()).setDisplayName(value.displayName())
                .setMediaType(value.mediaType()).setActualBytes(value.actualBytes())
                .setUploaderActorId(value.uploaderActorId()).setCreatedTime(value.createdTime())
                .setAvailableTime(value.availableTime()).setVersion(value.version());
    }
}
