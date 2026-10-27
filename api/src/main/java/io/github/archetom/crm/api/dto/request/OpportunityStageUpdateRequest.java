package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class OpportunityStageUpdateRequest {
    @NotNull
    @PositiveOrZero
    private Long version;
    @NotBlank
    @Size(max = 64)
    private String pipelineCode;
    @NotBlank
    @Size(max = 64)
    private String stageCode;
}
