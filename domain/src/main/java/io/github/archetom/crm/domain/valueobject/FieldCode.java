package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

import java.util.regex.Pattern;

/** Stable field identifier safe for metadata lookup; it is never used as raw SQL. */
@Value
public class FieldCode implements ValueObject<FieldCode> {

    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9_]{1,63}");

    String value;

    public FieldCode(String value) {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("field code is invalid");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(FieldCode other) {
        return other != null && value.equals(other.value);
    }
}
