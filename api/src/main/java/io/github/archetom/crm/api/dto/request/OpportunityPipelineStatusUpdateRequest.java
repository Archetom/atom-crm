package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class OpportunityPipelineStatusUpdateRequest {
    @NotNull
    @Min(0)
    private Long version;
    @NotBlank
    @Size(max = 20)
    private String status;
}
