package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ActivityRelationRequest {
    @NotBlank
    private String objectType;
    @NotNull @Positive
    private Long recordId;
}
