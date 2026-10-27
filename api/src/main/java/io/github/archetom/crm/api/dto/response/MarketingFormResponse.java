package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
public class MarketingFormResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String publicId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"CUSTOMER", "LEAD"})
    private String targetObjectType;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;
    private String description;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String submitButtonText;
    private String successMessage;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long ownerActorId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"INACTIVE", "ACTIVE", "DELETED"})
    private String status;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = MarketingFormFieldResponse.class))
    private List<MarketingFormFieldResponse> fields;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long version;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime createdTime;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime updatedTime;
}
