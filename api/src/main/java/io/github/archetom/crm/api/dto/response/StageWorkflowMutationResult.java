package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Version baseline returned after changing a workflow definition. */
public record StageWorkflowMutationResult(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") Long version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"ACTIVE", "INACTIVE", "DELETED"}) String status) {
}
