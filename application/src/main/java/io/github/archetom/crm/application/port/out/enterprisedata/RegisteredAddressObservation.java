package io.github.archetom.crm.application.port.out.enterprisedata;

import java.util.Locale;
import java.util.regex.Pattern;

/** One allowlisted registered-address observation, never a provider address map. */
public record RegisteredAddressObservation(
        String countryCode,
        String region,
        String locality,
        String addressLine,
        String postalCode
) {

    private static final Pattern COUNTRY = Pattern.compile("[A-Z]{2}");

    public RegisteredAddressObservation {
        countryCode = normalizeCountry(countryCode);
        region = optional(region, 100, "address region");
        locality = optional(locality, 200, "address locality");
        addressLine = required(addressLine, 500, "address line");
        postalCode = optional(postalCode, 32, "address postal code");
    }

    @Override
    public String toString() {
        return "RegisteredAddressObservation[redacted]";
    }

    private static String normalizeCountry(String value) {
        String supplied = optional(value, 2, "address country code");
        if (supplied == null) {
            return null;
        }
        String normalized = supplied.toUpperCase(Locale.ROOT);
        if (!COUNTRY.matcher(normalized).matches()) {
            throw new IllegalArgumentException("address country code is invalid");
        }
        return normalized;
    }

    private static String required(String value, int maxCodePoints, String name) {
        String normalized = optional(value, maxCodePoints, name);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String optional(String value, int maxCodePoints, String name) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > maxCodePoints) {
            throw new IllegalArgumentException(name + " is too long");
        }
        return normalized;
    }
}
