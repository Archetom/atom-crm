package io.github.archetom.crm.domain.valueobject;

import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * username value object
 * @author hanfeng
 */
@Value
public class Username implements ValueObject<Username> {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,50}$");

    String value;

    public Username(String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("Username must not be empty");
        }
        if (!USERNAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Username must contain 3-50 letters, digits, or underscores");
        }
        this.value = value;
    }

    public static Username of(String value) {
        return new Username(value);
    }

    @Override
    public boolean sameValueAs(Username other) {
        return other != null && this.value.equals(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
