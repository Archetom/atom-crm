package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/** Anonymous form values; tenant, owner, status, and unconfigured fields are never accepted. */
@Data
@Accessors(chain = true)
public class MarketingFormSubmissionRequest {
    /** Browser-generated retry key. It is persisted only as a scoped submission idempotency key. */
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
    private String idempotencyKey;

    @NotNull
    @Size(max = 50)
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 20) List<@NotBlank @Size(max = 500) String>> values;

    /** Separate structured FILE channel; a file identifier can never be a scalar value. */
    @Positive
    private Long fileDraftId;
    @Size(max = 50)
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 10) List<@NotNull @Positive Long>> fileFields;

    /** Public submission is a strict whitelist; identity and lifecycle fields are rejected. */
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object ignoredValue) {
        throw new IllegalArgumentException("Unknown marketing form submission property: " + name);
    }
}
