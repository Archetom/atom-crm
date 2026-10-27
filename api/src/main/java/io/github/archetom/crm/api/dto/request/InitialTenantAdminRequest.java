package io.github.archetom.crm.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/** One-time public request that creates the first local tenant owner account. */
@Data
@Accessors(chain = true)
public class InitialTenantAdminRequest {

    @NotBlank
    @Size(max = 100)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "系统管理员")
    private String displayName;

    @NotBlank
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[A-Za-z0-9_]{3,50}$")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "workspace_admin")
    private String loginName;

    @NotBlank
    @Email
    @Size(max = 254)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "admin@example.com")
    private String email;

    @NotBlank
    @Size(min = 12, max = 64)
    @ToString.Exclude
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "password", writeOnly = true)
    private String password;
}
