package io.github.archetom.crm.domain.valueobject;

import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/** Validated E.164 phone number with safe display behavior. */
@Value
public class PhoneNumber implements ValueObject<PhoneNumber> {

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\+[1-9]\\d{7,14}$"
    );

    String value;

    public PhoneNumber(String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("Phone number cannot be blank");
        }
        if (!PHONE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Phone number must use E.164 format");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(PhoneNumber other) {
        return other != null && this.value.equals(other.value);
    }

    /** Return a masked representation that retains only a short prefix and suffix. */
    public String getMasked() {
        if (value.length() > 8) {
            int prefixLength = 4;
            int suffixLength = 4;
            int hiddenLength = value.length() - prefixLength - suffixLength;
            return value.substring(0, prefixLength)
                    + "*".repeat(hiddenLength)
                    + value.substring(value.length() - suffixLength);
        }
        return "[REDACTED]";
    }

    @Override
    public String toString() {
        return getMasked();
    }
}
