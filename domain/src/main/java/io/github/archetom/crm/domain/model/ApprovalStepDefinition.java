package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.exception.ApprovalDomainException;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.RoleId;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Immutable configuration for one conditional approval step. */
public record ApprovalStepDefinition(
        String code,
        String name,
        int order,
        ApprovalStepType type,
        ApprovalStepMode mode,
        ApprovalFallback fallback,
        List<MemberId> memberIds,
        List<RoleId> roleIds,
        List<FilterCondition> conditions) {

    public static final int MAX_ACTORS = 100;
    public static final int MAX_CONDITIONS = 20;

    public ApprovalStepDefinition {
        code = requireCode(code);
        name = requireText(name, 100, "approval step name");
        if (order < 0 || type == null || mode == null || fallback == null) {
            throw new ApprovalDomainException("approval step configuration is invalid");
        }
        memberIds = distinct(memberIds, MAX_ACTORS, "approval step members");
        roleIds = distinct(roleIds, MAX_ACTORS, "approval step roles");
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        if (conditions.size() > MAX_CONDITIONS || conditions.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ApprovalDomainException("approval step conditions are invalid");
        }
        if (type == ApprovalStepType.MEMBER && memberIds.isEmpty()
                || type == ApprovalStepType.ROLE && roleIds.isEmpty()
                || type == ApprovalStepType.APPLICANT_SELECT
                && (!memberIds.isEmpty() || !roleIds.isEmpty())
                || type != ApprovalStepType.MEMBER && !memberIds.isEmpty()
                || type != ApprovalStepType.ROLE && !roleIds.isEmpty()) {
            throw new ApprovalDomainException("approval step assignee configuration is invalid");
        }
    }

    private static String requireCode(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || !normalized.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new ApprovalDomainException("approval step code is invalid");
        }
        return normalized;
    }

    private static String requireText(String value, int maximum, String description) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > maximum) {
            throw new ApprovalDomainException(description + " is invalid");
        }
        return normalized;
    }

    private static <T> List<T> distinct(List<T> values, int maximum, String description) {
        List<T> source = values == null ? List.of() : List.copyOf(values);
        Set<T> unique = new LinkedHashSet<>(source);
        if (source.size() > maximum || unique.size() != source.size() || unique.contains(null)) {
            throw new ApprovalDomainException(description + " are invalid");
        }
        return List.copyOf(unique);
    }
}
