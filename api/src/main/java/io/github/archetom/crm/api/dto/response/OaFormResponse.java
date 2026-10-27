package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
public class OaFormResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    private String description;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long approvalDefinitionId;
    private String approvalDefinitionName;
    private String approvalDefinitionStatus;
    private Long createdByActorId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"INACTIVE", "ACTIVE", "DELETED"})
    private String status;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = OaFormApprovalSelectionResponse.class))
    private List<OaFormApprovalSelectionResponse> approvalSelections;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = OaFormFieldResponse.class))
    private List<OaFormFieldResponse> fields;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long version;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime createdTime;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime updatedTime;
}
