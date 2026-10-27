package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

public record OaRequestResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long formId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") Long formVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long approvalInstanceId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"PENDING", "APPROVED", "REJECTED", "WITHDRAWN", "CANCELLED"}) String approvalStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, List<String>> values,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, List<AttachmentResponse>> fileFields,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") Long version) {
}
