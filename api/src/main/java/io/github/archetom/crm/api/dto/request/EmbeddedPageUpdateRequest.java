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
public class EmbeddedPageUpdateRequest {
    @NotNull
    @Min(0)
    private Long version;
    @NotBlank
    @Size(max = 100)
    private String name;
    @Size(max = 500)
    private String description;
    @NotBlank
    @Size(max = 1000)
    private String urlTemplate;
    @Min(0)
    @Max(10000)
    private Integer sortOrder = 0;
    @NotBlank
    @Size(max = 20)
    private String status;
}
