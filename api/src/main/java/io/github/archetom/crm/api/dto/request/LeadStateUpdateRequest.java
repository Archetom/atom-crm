package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class LeadStateUpdateRequest {
    @NotNull
    @PositiveOrZero
    private Long version;
    @NotNull
    private Boolean locked;
    @NotBlank
    private String dealStatus;
}
