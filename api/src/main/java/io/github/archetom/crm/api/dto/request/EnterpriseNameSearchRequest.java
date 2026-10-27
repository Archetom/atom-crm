package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.github.archetom.crm.api.validation.TrimmedCodePointSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/** One bounded registered-name search with an explicit governed jurisdiction. */
@Data
@Accessors(chain = true)
public class EnterpriseNameSearchRequest {

    @NotBlank
    @TrimmedCodePointSize(max = 200)
    @ToString.Exclude
    private String registeredName;

    @NotBlank
    @Pattern(regexp = "^[A-Za-z]{2}(?:-[A-Za-z0-9]{1,3})?$")
    private String legalJurisdiction;

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object ignoredValue) {
        throw new IllegalArgumentException("Unknown enterprise name search property");
    }
}
