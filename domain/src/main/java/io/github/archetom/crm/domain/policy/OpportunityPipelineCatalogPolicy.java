package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.exception.OpportunityPipelineDomainException;

import java.util.regex.Pattern;

/** Fixed exact-code and bounded display-value rules for the Opportunity sales catalog. */
public final class OpportunityPipelineCatalogPolicy {

    public static final int MAX_CODE_LENGTH = 64;
    public static final int MAX_NAME_LENGTH = 200;
    public static final int MAX_DISPLAY_ORDER = 1_000_000;
    public static final int MAX_PROBABILITY = 100;

    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private OpportunityPipelineCatalogPolicy() {
    }

    /** Trims only surrounding whitespace; identity remains byte-for-byte exact afterwards. */
    public static String requireCode(String value) {
        if (value == null) {
            throw new OpportunityPipelineDomainException("opportunity pipeline code is required");
        }
        String code = value.trim();
        if (!CODE.matcher(code).matches()) {
            throw new OpportunityPipelineDomainException("opportunity pipeline code is invalid");
        }
        return code;
    }

    public static String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new OpportunityPipelineDomainException("opportunity pipeline name is required");
        }
        String name = value.trim();
        if (name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) {
            throw new OpportunityPipelineDomainException("opportunity pipeline name is too long");
        }
        return name;
    }

    public static int requireDisplayOrder(Integer value) {
        if (value == null || value < 0 || value > MAX_DISPLAY_ORDER) {
            throw new OpportunityPipelineDomainException("opportunity pipeline display order is invalid");
        }
        return value;
    }

    public static int requireProbability(Integer value) {
        if (value == null || value < 0 || value > MAX_PROBABILITY) {
            throw new OpportunityPipelineDomainException("opportunity pipeline probability is invalid");
        }
        return value;
    }
}
