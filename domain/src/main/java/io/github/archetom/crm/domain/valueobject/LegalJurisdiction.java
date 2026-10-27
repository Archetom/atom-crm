package io.github.archetom.crm.domain.valueobject;

import java.util.Locale;
import java.util.regex.Pattern;

/** Governed ISO country or country-subdivision legal jurisdiction. */
public record LegalJurisdiction(String value) implements ValueObject<LegalJurisdiction> {

    private static final Pattern FORMAT = Pattern.compile("[A-Z]{2}(?:-[A-Z0-9]{1,3})?");

    public LegalJurisdiction {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("legal jurisdiction must not be blank");
        }
        value = value.trim().toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("legal jurisdiction is invalid");
        }
    }

    @Override
    public boolean sameValueAs(LegalJurisdiction other) {
        return other != null && value.equals(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
