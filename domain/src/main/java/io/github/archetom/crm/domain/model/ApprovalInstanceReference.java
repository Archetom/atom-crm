package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;

/** Lightweight approval root reference used for visibility scans. */
public record ApprovalInstanceReference(
        ApprovalInstanceId instanceId,
        BusinessObjectType objectType,
        Long subjectId) {

    public ApprovalInstanceReference {
        if (instanceId == null || objectType == null || subjectId == null || subjectId <= 0) {
            throw new IllegalArgumentException("approval instance reference is invalid");
        }
    }
}
