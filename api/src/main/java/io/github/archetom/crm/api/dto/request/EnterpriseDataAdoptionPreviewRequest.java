package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/** Caller-bound exact lookup and B05/B06 diff preview for one existing Customer. */
@Data
@Accessors(chain = true)
public class EnterpriseDataAdoptionPreviewRequest {
    @NotNull @Positive private Long customerId;
    @NotNull @PositiveOrZero private Long customerVersion;
    @NotBlank @Size(max = 64) private String identityScope;
    @NotBlank @Size(max = 64) private String registryScheme;
    @NotBlank @Size(max = 64) @ToString.Exclude private String value;
    @NotBlank @Size(max = 64) private String addressSlotKey;

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object ignoredValue) {
        throw new IllegalArgumentException("Unknown enterprise data adoption preview property");
    }
}
