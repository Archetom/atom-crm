package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record AttachmentUploadIntentVO(Long fileId, String displayName, String declaredMediaType,
                                       Long expectedBytes, String status, Long version,
                                       LocalDateTime expiresAt, Long draftId) { }
