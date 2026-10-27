package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/** Strict OA preview input; identity, tenant, department, and facts are server-owned. */
@Data
@Accessors(chain = true)
public class OaRequestPreviewRequest {
    @NotNull
    @Positive
    private Long formId;
    @NotNull
    @PositiveOrZero
    private Long formVersion;
    @NotNull
    @Size(max = 100)
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 20) List<@NotNull @Size(max = 500) String>> values;
    @Positive
    private Long fileFieldDraftId;
    @Size(max = 100)
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 10) List<@NotNull @Positive Long>> fileFields;
    @Size(max = 50)
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 100) List<@NotNull @Positive Long>> selectedApprovers;

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object ignoredValue) {
        throw new IllegalArgumentException("Unknown OA request preview property: " + name);
    }
}
