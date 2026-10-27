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
public class MarketingFormFieldRequest {
    @NotBlank
    @Size(max = 64)
    private String fieldCode;
    @NotBlank
    @Size(max = 100)
    private String label;
    @Size(max = 200)
    private String placeholder;
    @Size(max = 500)
    private String helpText;
    @NotNull
    private Boolean required;
    @NotNull
    @Min(0)
    @Max(10000)
    private Integer sortOrder;
}
