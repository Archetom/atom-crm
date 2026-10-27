package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** Structured current value for a writable ADDRESS stage field. */
public record StageAddressValueResponse(
        String country,
        String region,
        String city,
        String postalCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String addressLine,
        BigDecimal longitude,
        BigDecimal latitude) {
}
