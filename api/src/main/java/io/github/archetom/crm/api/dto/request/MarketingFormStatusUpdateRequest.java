package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MarketingFormStatusUpdateRequest {
    @NotNull
    @PositiveOrZero
    private Long version;
    @NotBlank
    private String status;
}
