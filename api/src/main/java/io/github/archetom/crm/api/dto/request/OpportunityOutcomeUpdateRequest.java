package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class OpportunityOutcomeUpdateRequest {
    @NotNull
    @PositiveOrZero
    private Long version;
    @NotBlank
    @Size(max = 20)
    private String outcome;
    @Size(max = 1000)
    private String remark;
}
