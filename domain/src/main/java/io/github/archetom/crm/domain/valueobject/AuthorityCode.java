package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

import java.util.regex.Pattern;

/** Stable code-defined application capability. */
@Value
public class AuthorityCode implements ValueObject<AuthorityCode> {

    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9-]*:[a-z][a-z0-9-]*");

    String value;

    public AuthorityCode(String value) {
        if (value == null || value.isBlank() || value.length() > 100 || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("authority code is invalid");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(AuthorityCode other) {
        return other != null && value.equals(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
