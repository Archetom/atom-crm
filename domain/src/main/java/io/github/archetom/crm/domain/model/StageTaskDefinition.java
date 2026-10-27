package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.StageWorkflowDomainException;

public record StageTaskDefinition(String code, String name, boolean required) {
    public StageTaskDefinition {
        code = normalizeCode(code, "stage task code");
        name = normalizeText(name, 100, "stage task name");
    }

    static String normalizeCode(String value, String description) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || !normalized.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new StageWorkflowDomainException(description + " is invalid");
        }
        return normalized;
    }

    static String normalizeText(String value, int maximum, String description) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > maximum) {
            throw new StageWorkflowDomainException(description + " is invalid");
        }
        return normalized;
    }
}
