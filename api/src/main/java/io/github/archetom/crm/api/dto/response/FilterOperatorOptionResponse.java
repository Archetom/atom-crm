package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** A saved-view operator and the exact number of values accepted by the server. */
public record FilterOperatorOptionResponse(
        @Schema(allowableValues = {"EQ", "NOT_EQ", "CONTAINS", "NOT_CONTAINS", "IN",
                "GT", "GTE", "LT", "LTE", "BETWEEN", "IS_EMPTY", "IS_NOT_EMPTY"},
                requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") int minimumValueCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") int maximumValueCount) {
}
