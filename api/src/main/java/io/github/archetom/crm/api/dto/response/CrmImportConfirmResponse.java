package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class CrmImportConfirmResponse {
    private Long draftId;
    private String objectType;
    private String status;
    private Integer importedRows;
    private Long version;
}
