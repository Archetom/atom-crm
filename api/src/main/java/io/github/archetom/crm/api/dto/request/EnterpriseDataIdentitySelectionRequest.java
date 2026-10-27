package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

/** Selects one identity from the server-held, exact-relooked observation. */
@Data
@Accessors(chain = true)
public class EnterpriseDataIdentitySelectionRequest {
    @NotBlank @Size(max = 64) private String identityScope;
    @NotBlank @Size(max = 64) private String registryScheme;

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object ignoredValue) {
        throw new IllegalArgumentException("Unknown enterprise identity selection property");
    }
}
