package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.CustomerDomainException;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Validated address value owned by a customer aggregate. */
public record CustomerAddress(
        String key,
        String label,
        String country,
        String region,
        String city,
        String postalCode,
        String addressLine,
        BigDecimal longitude,
        BigDecimal latitude,
        boolean primary) {

    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    public CustomerAddress {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new CustomerDomainException("customer address key is invalid");
        }
        key = key.toLowerCase(java.util.Locale.ROOT);
        label = optional(label, 100, "customer address label");
        country = optional(country, 100, "customer address country");
        region = optional(region, 100, "customer address region");
        city = optional(city, 100, "customer address city");
        postalCode = optional(postalCode, 30, "customer address postal code");
        addressLine = required(addressLine, 500, "customer address line");
        requireCoordinate(longitude, new BigDecimal("-180"), new BigDecimal("180"), "longitude");
        requireCoordinate(latitude, new BigDecimal("-90"), new BigDecimal("90"), "latitude");
        if ((longitude == null) != (latitude == null)) {
            throw new CustomerDomainException("customer address coordinates must be provided together");
        }
    }

    private static String required(String value, int max, String description) {
        String normalized = optional(value, max, description);
        if (normalized == null) {
            throw new CustomerDomainException(description + " must not be empty");
        }
        return normalized;
    }

    private static String optional(String value, int max, String description) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new CustomerDomainException(description + " is too long");
        }
        return normalized;
    }

    private static void requireCoordinate(
            BigDecimal value, BigDecimal minimum, BigDecimal maximum, String description) {
        if (value != null && (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0)) {
            throw new CustomerDomainException("customer address " + description + " is invalid");
        }
    }
}
