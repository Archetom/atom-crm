package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Exact typed registry selector. The public vocabulary includes CN USCC, LEI,
 * and Companies House numbers; values are revalidated by the domain catalog.
 */
@Data
@Accessors(chain = true)
public class EnterpriseIdentifierLookupRequest {

    @NotBlank
    @Size(max = 64)
    private String identityScope;

    @NotBlank
    @Size(max = 64)
    private String registryScheme;

    @NotBlank
    @Size(max = 64)
    @ToString.Exclude
    private String value;

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object ignoredValue) {
        throw new IllegalArgumentException("Unknown enterprise identifier lookup property");
    }
}
