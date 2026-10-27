package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class RoleFieldAccessRequest {
    @NotBlank
    @Size(max = 64)
    private String fieldCode;
    @NotBlank
    @Size(max = 20)
    @Schema(allowableValues = {"HIDDEN", "READ", "WRITE"})
    private String accessLevel;
    @NotBlank
    @Size(max = 20)
    @Schema(allowableValues = {"NONE", "PARTIAL", "FULL"})
    private String maskType = "NONE";
}
