package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Public rendering contract without tenant, owner, form ID, or version metadata. */
@Data
@Accessors(chain = true)
public class PublicMarketingFormResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Opaque public identifier; it does not grant an authenticated tenant context.")
    private String publicId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;
    private String description;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String submitButtonText;
    /** Configured completion copy; safe for anonymous renderers and contains no tenant metadata. */
    private String successMessage;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = MarketingFormFieldResponse.class))
    private List<MarketingFormFieldResponse> fields;
}
