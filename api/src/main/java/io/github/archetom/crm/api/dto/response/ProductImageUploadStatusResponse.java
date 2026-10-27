package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProductImageUploadStatusResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long draftId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"PRIMARY", "DETAIL"}) String imageType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long fileId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String mediaType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") Long size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"PENDING", "UPLOADED", "FAILED"}) String status) { }
