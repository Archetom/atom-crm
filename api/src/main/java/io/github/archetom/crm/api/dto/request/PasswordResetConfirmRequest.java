package io.github.archetom.crm.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/** Consumes one password-reset token and sets a replacement password. */
@Data
@Accessors(chain = true)
public class PasswordResetConfirmRequest {

    @NotBlank
    @Size(max = 1024)
    @ToString.Exclude
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, writeOnly = true)
    private String resetToken;

    @NotBlank
    @Size(min = 12, max = 64)
    @ToString.Exclude
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "password", writeOnly = true)
    private String newPassword;
}
