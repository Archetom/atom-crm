package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class StageWorkflowStatusRequest {
    @NotNull
    @Min(0)
    private Long version;
    @NotBlank
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"ACTIVE", "INACTIVE", "DELETED"})
    private String status;
}
