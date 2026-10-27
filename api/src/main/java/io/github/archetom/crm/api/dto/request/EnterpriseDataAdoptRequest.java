package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.List;

/** Explicit selected-fact adoption. No source value, locator, policy, tenant or actor is accepted. */
@Data
@Accessors(chain = true)
public class EnterpriseDataAdoptRequest {
    @NotBlank @Size(max = 128) @ToString.Exclude private String previewReference;
    @NotNull @PositiveOrZero private Long customerVersion;
    @NotEmpty @Size(max = 3) private List<@NotBlank @Size(max = 64) String> facts;
    @NotEmpty @Size(max = 20) @Valid private List<EnterpriseDataIdentitySelectionRequest> identities;

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object ignoredValue) {
        throw new IllegalArgumentException("Unknown enterprise data adoption property");
    }
}
