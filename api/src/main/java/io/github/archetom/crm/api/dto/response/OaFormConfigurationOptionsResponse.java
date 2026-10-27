package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Server-filtered choices for configuring an OA form without accepting raw internal IDs. */
public record OaFormConfigurationOptionsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<OaFormApprovalDefinitionCandidateResponse> approvalDefinitions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<OaFormConfigurationFieldResponse> fields) {
}
