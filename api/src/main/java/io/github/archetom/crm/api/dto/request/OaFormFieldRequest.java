package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class OaFormFieldRequest {
    @NotBlank
    @Size(max = 64)
    private String fieldCode;
    @NotNull
    private Boolean required;
    @NotNull
    @Min(0)
    @Max(10000)
    private Integer sortOrder;
}
