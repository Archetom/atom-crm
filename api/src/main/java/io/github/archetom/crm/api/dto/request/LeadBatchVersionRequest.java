package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.experimental.Accessors;

/** One explicit, versioned lead target for an atomic bulk command. */
@Data
@Accessors(chain = true)
public class LeadBatchVersionRequest {
    @NotNull
    @Positive
    private Long leadId;

    @NotNull
    @PositiveOrZero
    private Long version;
}
