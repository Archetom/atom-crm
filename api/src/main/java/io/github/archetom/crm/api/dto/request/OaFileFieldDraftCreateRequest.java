package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/** Pins an OA FILE draft to the currently reviewed form version. */
@Data
public class OaFileFieldDraftCreateRequest {
    @NotNull
    @PositiveOrZero
    private Long formVersion;
}
