package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.experimental.Accessors;

/** One explicit, versioned marketing-activity target for a bulk command. */
@Data
@Accessors(chain = true)
public class MarketingActivityVersionRequest {
    @NotNull
    @Positive
    private Long activityId;

    @NotNull
    @PositiveOrZero
    private Long version;
}
