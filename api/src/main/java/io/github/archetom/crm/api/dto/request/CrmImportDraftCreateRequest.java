package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = false)
public class CrmImportDraftCreateRequest {
    /** LEAD or CONTACT. The server owns the authority mapping. */
    @NotBlank
    private String objectType;
}
