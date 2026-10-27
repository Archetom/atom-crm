package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

import java.util.regex.Pattern;

/**
 * email value object
 * @author hanfeng
 */
@Value
public class Email implements ValueObject<Email> {

    private static final int MAX_LENGTH = 254;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"
    );

    String value;

    public Email(String value) {
        if (value == null || value.length() > MAX_LENGTH || !EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid email format or length");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(Email other) {
        return other != null && this.value.equals(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
