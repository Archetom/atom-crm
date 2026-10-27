package io.github.archetom.crm.domain.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Closed activity categories published to marketing-activity editors.
 *
 * <p>The stored activity type remains a string so records created before this
 * catalog can still be read and edited without data migration. New commands
 * use one of these stable codes.</p>
 */
public enum MarketingActivityType {
    ADVERTISING("广告"),
    SEMINAR("研讨会"),
    CONFERENCE("会议"),
    EMAIL_CAMPAIGN("邮件营销"),
    PUBLIC_RELATIONS("公共关系"),
    PARTNER("合作伙伴");

    private final String displayName;

    MarketingActivityType(String displayName) {
        this.displayName = displayName;
    }

    public String code() {
        return name();
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<MarketingActivityType> fromCode(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(candidate -> candidate.name().equals(normalized)).findFirst();
    }
}
