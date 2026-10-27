package io.github.archetom.crm.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/** Verifies tenant-scoped credentials before exposing the member's tenant choices. */
@Data
@Accessors(chain = true)
public class PasswordLoginOptionsRequest {

    @NotNull
    @Positive
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "900001")
    private Long tenantId;

    @NotBlank
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[A-Za-z0-9_]{3,50}$")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "linhuiba_admin")
    private String loginName;

    @NotBlank
    @Size(min = 1, max = 72)
    @ToString.Exclude
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "password", writeOnly = true)
    private String password;
}
