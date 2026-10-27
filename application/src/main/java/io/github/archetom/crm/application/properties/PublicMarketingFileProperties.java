package io.github.archetom.crm.application.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Independent opt-in gate for anonymous MarketingForm FILE admission. */
@Data
@ConfigurationProperties(prefix = "atom.files.public-marketing")
public class PublicMarketingFileProperties {
    public static final int MIN_REQUESTS_PER_MINUTE = 1;
    public static final int MAX_REQUESTS_PER_FORM_PER_MINUTE = 600;
    public static final int MAX_REQUESTS_PER_SOURCE_PER_MINUTE = 120;

    private boolean enabled;
    private Duration draftTtl = Duration.ofMinutes(30);
    private int maxFilesPerDraft = 10;
    private long maxBytesPerDraft = 33_554_432L;
    private int maxActiveDraftsPerForm = 100;
    private int maxRequestsPerFormPerMinute = 120;
    private int maxRequestsPerSourcePerMinute = 30;

    public boolean valid() {
        return within(draftTtl, Duration.ofMinutes(5), Duration.ofHours(2))
                && maxFilesPerDraft >= 1 && maxFilesPerDraft <= 10
                && maxBytesPerDraft >= 1 && maxBytesPerDraft <= 67_108_864L
                && maxActiveDraftsPerForm >= 1 && maxActiveDraftsPerForm <= 100
                && validRequestLimits(maxRequestsPerFormPerMinute, maxRequestsPerSourcePerMinute);
    }

    /** Shared admission bound used by both configuration health and the REST limiter. */
    public static boolean validRequestLimits(int perForm, int perSource) {
        return perForm >= MIN_REQUESTS_PER_MINUTE
                && perForm <= MAX_REQUESTS_PER_FORM_PER_MINUTE
                && perSource >= MIN_REQUESTS_PER_MINUTE
                && perSource <= MAX_REQUESTS_PER_SOURCE_PER_MINUTE;
    }

    private static boolean within(Duration value, Duration min, Duration max) {
        return value != null && value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }
}
