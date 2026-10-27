package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AttachmentUploadStatusResponse {
    private String status;
    private String failureCategory;
    private Long version;
}
