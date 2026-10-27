package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class StageOutcomeRequest {
    @NotNull
    @Min(0)
    private Long version;
    @NotBlank
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"FAILED"},
            description = "A value advertised by record.allowedOutcomes")
    private String outcome;
    @Size(max = 1000)
    private String remark;
}
