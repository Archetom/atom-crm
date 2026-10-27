package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.exception.CustomerDomainException;

import java.text.Normalizer;
import java.util.Locale;

/** Canonical identity for customer tags while preserving their validated display names. */
public final class CustomerTagPolicy {

    public static final int MAX_KEY_LENGTH = 500;

    private CustomerTagPolicy() {
    }

    public static String key(String tagName) {
        if (tagName == null || tagName.isBlank()) {
            throw new CustomerDomainException("customer tag must not be empty");
        }
        String key = Normalizer.normalize(tagName.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        if (key.length() > MAX_KEY_LENGTH) {
            throw new CustomerDomainException("customer tag key is too long");
        }
        return key;
    }
}
