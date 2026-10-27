package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

import java.util.Base64;
import java.util.regex.Pattern;

/** Canonical, opaque, 256-bit connector locator. It carries no tenant information. */
@Value
public class ConnectorRef implements ValueObject<ConnectorRef> {
    private static final Pattern SHAPE = Pattern.compile("[A-Za-z0-9_-]{43}");
    String value;

    public ConnectorRef(String value) {
        if (!isCanonical(value)) {
            throw new IllegalArgumentException("connector reference is invalid");
        }
        this.value = value;
    }

    public static boolean isCanonical(String value) {
        if (value == null || !SHAPE.matcher(value).matches()) {
            return false;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            return decoded.length == 32
                    && Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public boolean sameValueAs(ConnectorRef other) {
        return other != null && value.equals(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
