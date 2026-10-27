package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class StageTaskRequest {
    @NotBlank
    @Size(max = 64)
    private String code;
    @NotBlank
    @Size(max = 100)
    private String name;
    @NotNull
    private Boolean required;
}
