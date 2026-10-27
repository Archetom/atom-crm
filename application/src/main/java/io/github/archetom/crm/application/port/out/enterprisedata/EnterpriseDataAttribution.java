package io.github.archetom.crm.application.port.out.enterprisedata;

import io.github.archetom.crm.domain.model.EnterpriseDataAttributionContext;

import java.net.URI;
import java.util.Objects;

/** Versioned-directory attribution resolved for one public display context. */
public record EnterpriseDataAttribution(
        EnterpriseDataAttributionContext context,
        String sourceName,
        String informationUrl,
        String disclaimer
) {
    public EnterpriseDataAttribution {
        Objects.requireNonNull(context, "attribution context must not be null");
        sourceName = required(sourceName, 100, "attribution source name");
        informationUrl = informationUrl(informationUrl);
        disclaimer = optional(disclaimer, 500, "attribution disclaimer");
    }

    private static String informationUrl(String value) {
        String normalized = optional(value, 500, "attribution information URL");
        if (normalized == null) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("attribution information URL is invalid", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("attribution information URL is invalid");
        }
        return uri.toASCIIString();
    }

    private static String required(String value, int maxCodePoints, String name) {
        String normalized = optional(value, maxCodePoints, name);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String optional(String value, int maxCodePoints, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > maxCodePoints) {
            throw new IllegalArgumentException(name + " is too long");
        }
        return normalized;
    }

    @Override
    public String toString() {
        return "EnterpriseDataAttribution[context=" + context + "]";
    }
}
