package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;

/** Structured, unmasked ADDRESS value supplied with a writable stage field. */
public record StageAddressValueVO(String country, String region, String city, String postalCode,
                                  String addressLine, BigDecimal longitude, BigDecimal latitude) {
}
