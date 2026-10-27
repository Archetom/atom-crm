package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record StageTaskResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
                                @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
                                @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean required) {
}
