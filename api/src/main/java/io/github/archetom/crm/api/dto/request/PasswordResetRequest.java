package io.github.archetom.crm.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

/** Requests a password-reset message for one tenant-scoped account. */
@Data
@Accessors(chain = true)
public class PasswordResetRequest {

    @NotNull
    @Positive
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "900001")
    private Long tenantId;

    @NotBlank
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[A-Za-z0-9_]{3,50}$")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "linhuiba_admin")
    private String loginName;
}
