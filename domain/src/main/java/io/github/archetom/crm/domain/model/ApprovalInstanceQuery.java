package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.valueobject.MemberId;

public record ApprovalInstanceQuery(
        MemberId memberId,
        ApprovalQueryBucket bucket,
        ApprovalInstanceStatus status,
        BusinessObjectType objectType,
        Long subjectId,
        String keyword,
        int page,
        int size) {

    public ApprovalInstanceQuery {
        if (memberId == null || bucket == null || page < 1 || size < 1 || size > 200
                || subjectId != null && subjectId <= 0) {
            throw new IllegalArgumentException("approval instance query is invalid");
        }
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (keyword != null && keyword.length() > 200) {
            throw new IllegalArgumentException("approval instance keyword is invalid");
        }
    }
}
