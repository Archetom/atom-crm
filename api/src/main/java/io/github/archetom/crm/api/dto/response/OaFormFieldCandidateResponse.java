package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** One available OA form value for a USER or DEPARTMENT input. */
public record OaFormFieldCandidateResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
                "ACTOR_ID", "DEPARTMENT_ID"}) String valueType) {
}
