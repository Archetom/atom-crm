package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class OpportunityStarUpdateRequest {
    @NotNull
    private Boolean starred;
}
