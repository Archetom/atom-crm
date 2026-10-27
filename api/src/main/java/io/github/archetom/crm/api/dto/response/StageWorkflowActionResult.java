package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** The refreshed resource projection after an advance or outcome action. */
public record StageWorkflowActionResult(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) StageWorkflowRecordResponse record) {
}
