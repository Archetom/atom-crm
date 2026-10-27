package io.github.archetom.crm.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/** Completes an already verified login by selecting one returned tenant. */
@Data
@Accessors(chain = true)
public class PasswordSessionCreateRequest {

    @NotBlank
    @Size(max = 1024)
    @ToString.Exclude
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, writeOnly = true)
    private String loginChallenge;

    @NotNull
    @Positive
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "900001")
    private Long tenantId;
}
