package io.github.archetom.crm.api.dto.request;

import io.github.archetom.crm.api.validation.TrimmedCodePointSize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class OpportunityPipelineCreateRequest {
    @NotBlank
    @TrimmedCodePointSize(max = 64)
    private String code;
    @NotBlank
    @TrimmedCodePointSize(max = 200)
    private String name;
    @NotNull
    @Min(0)
    @Max(1_000_000)
    private Integer displayOrder;
}
