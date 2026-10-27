package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.List;

/** Verified, short-lived tenant choices for a password-login flow. */
@Data
@Accessors(chain = true)
public class PasswordLoginOptionsResponse {

    @ToString.Exclude
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String loginChallenge;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<PasswordLoginTenantResponse> tenants;
}
