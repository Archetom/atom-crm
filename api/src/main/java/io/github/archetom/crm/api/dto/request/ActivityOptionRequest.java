package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ActivityOptionRequest {
    @NotBlank
    private String type;
    @NotBlank
    @Size(max = 64)
    private String value;
    @NotBlank
    @Size(max = 200)
    private String label;
    @Min(0)
    @Max(10_000)
    private Integer displayOrder;
}
