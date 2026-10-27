package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.exception.LeadDomainException;

import java.text.Normalizer;
import java.util.Locale;

/** Canonical identity for lead tags while preserving their validated display names. */
public final class LeadTagPolicy {

    public static final int MAX_KEY_LENGTH = 500;

    private LeadTagPolicy() {
    }

    public static String key(String tagName) {
        if (tagName == null || tagName.isBlank()) {
            throw new LeadDomainException("lead tag must not be empty");
        }
        String key = Normalizer.normalize(tagName.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        if (key.length() > MAX_KEY_LENGTH) {
            throw new LeadDomainException("lead tag key is too long");
        }
        return key;
    }
}
