package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class CrmImportPreviewResponse {
    private Long draftId;
    private String objectType;
    private String status;
    private Integer validRows;
    private List<IssueResponse> issues;

    @Data
    @Accessors(chain = true)
    public static class IssueResponse {
        private Integer rowNumber;
        /** Reviewed, non-sensitive machine code; raw source values never leave the server. */
        private String errCode;
    }
}
