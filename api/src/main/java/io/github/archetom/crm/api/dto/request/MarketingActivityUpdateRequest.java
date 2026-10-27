package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class MarketingActivityUpdateRequest extends MarketingActivityCreateRequest {
    @NotNull
    @PositiveOrZero
    private Long version;
}
