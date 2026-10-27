package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Type-specific, record-scoped input contract for one writable stage field. */
public record StageFieldInputResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
                "SCALAR_VALUES", "OPTION_VALUES", "ACTOR_IDS", "DEPARTMENT_IDS", "RELATION_IDS",
                "ADDRESS", "FILE_DRAFT", "UNSUPPORTED"}) String inputType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean candidatesRequired,
        @Schema(description = "Target object type for a RELATION candidate query.") String relationObjectType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "FILE values use the record-scoped file-draft endpoint, then fileFieldDraftId and fileFields.")
        boolean fileDraftRequired,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = StageAddressPartResponse.class))
        List<StageAddressPartResponse> addressParts,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean supported,
        @Schema(description = "Stable reason when inputType is UNSUPPORTED.") String blockerCode) {
}
