package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Creates a one-shot CRM pre-create FILE-field draft. */
@Data
public class FileFieldDraftCreateRequest {
    @NotBlank
    @Size(max = 30)
    private String objectType;
}
