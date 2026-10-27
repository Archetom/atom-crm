package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

/** One code-defined capability and its optional tenant-bounded data scope. */
@Data
@Accessors(chain = true)
public class RoleGrantRequest {

    @NotBlank
    @Size(max = 100)
    private String authorityCode;

    @Size(max = 30)
    private String dataScope;
}
