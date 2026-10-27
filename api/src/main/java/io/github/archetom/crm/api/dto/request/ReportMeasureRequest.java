package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ReportMeasureRequest {
    @NotBlank
    @Size(max = 20)
    private String type;
    @Size(max = 64)
    private String fieldCode;
}
