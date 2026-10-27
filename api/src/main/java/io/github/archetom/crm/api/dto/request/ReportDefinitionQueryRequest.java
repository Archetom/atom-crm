package io.github.archetom.crm.api.dto.request;

import io.github.archetom.crm.api.validation.TrimmedCodePointSize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ReportDefinitionQueryRequest {
    @Size(max = 30)
    private String objectType;
    @TrimmedCodePointSize(max = 100)
    private String keyword;
    @Min(1)
    private Integer page = 1;
    @Min(1) @Max(100)
    private Integer size = 20;
}
