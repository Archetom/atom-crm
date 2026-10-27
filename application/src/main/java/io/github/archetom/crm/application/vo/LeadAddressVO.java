package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;

public record LeadAddressVO(
        String key, String label, String country, String region, String city,
        String postalCode, String addressLine, BigDecimal longitude,
        BigDecimal latitude, boolean primary) {
}
