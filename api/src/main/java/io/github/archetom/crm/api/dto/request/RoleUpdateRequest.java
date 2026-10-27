package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

/** Metadata and lifecycle update for a custom role. */
@Data
@Accessors(chain = true)
public class RoleUpdateRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 500)
    private String description;

    @NotBlank
    @Pattern(regexp = "ACTIVE|INACTIVE")
    private String status;

    @NotNull
    @Min(0)
    private Long version;
}
