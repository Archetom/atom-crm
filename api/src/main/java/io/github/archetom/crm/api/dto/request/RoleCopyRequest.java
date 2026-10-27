package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

/** Name for a custom role copied without assignments. */
@Data
@Accessors(chain = true)
public class RoleCopyRequest {

    @NotBlank
    @Size(max = 100)
    private String name;
}
