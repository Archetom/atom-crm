package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.exception.ContactDomainException;

import java.text.Normalizer;
import java.util.Locale;

/** Canonical identity for contact tags while preserving their validated display names. */
public final class ContactTagPolicy {

    public static final int MAX_KEY_LENGTH = 500;

    private ContactTagPolicy() {
    }

    public static String key(String tagName) {
        if (tagName == null || tagName.isBlank()) {
            throw new ContactDomainException("contact tag must not be empty");
        }
        String key = Normalizer.normalize(tagName.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        if (key.length() > MAX_KEY_LENGTH) {
            throw new ContactDomainException("contact tag key is too long");
        }
        return key;
    }
}
