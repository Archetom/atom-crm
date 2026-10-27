package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class AttachmentUploadIntentResponse {
    /** Present only for the direct FILE-candidate endpoint. */
    private Long draftId;
    private Long fileId;
    private String displayName;
    private String declaredMediaType;
    private Long expectedBytes;
    private String status;
    private Long version;
    private LocalDateTime expiresAt;
}
