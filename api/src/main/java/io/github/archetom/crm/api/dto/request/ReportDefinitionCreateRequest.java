package io.github.archetom.crm.api.dto.request;

import io.github.archetom.crm.api.validation.TrimmedCodePointSize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class ReportDefinitionCreateRequest {
    @NotBlank
    @TrimmedCodePointSize(max = 100)
    private String name;
    @TrimmedCodePointSize(max = 500)
    private String description;
    @NotBlank @Size(max = 30)
    private String objectType;
    @NotBlank @Size(max = 64)
    private String timeFieldCode;
    @NotBlank @Size(max = 10)
    private String granularity;
    @Size(max = 64)
    private String dimensionFieldCode;
    @Size(max = 20)
    private List<@Valid FilterConditionRequest> filters = List.of();
    @Size(min = 1, max = 8)
    private List<@Valid ReportMeasureRequest> measures = List.of();
}
