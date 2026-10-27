package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class CrmImportDraftResponse {
    private Long draftId;
    private String objectType;
    private String status;
    private Long version;
    private LocalDateTime expiresAt;
    private Integer importedRows;
    /** Fixed controlled attachment target token for this private source CSV. */
    private String attachmentTargetType;
}
