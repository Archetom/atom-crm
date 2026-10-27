package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.entity.ApprovalInstance;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Approval root projection that deliberately excludes task and participant collections. */
public record ApprovalInstanceSummary(
        ApprovalInstanceId id,
        ApprovalDefinitionId definitionId,
        Long definitionVersion,
        BusinessObjectType objectType,
        Long subjectId,
        String idempotencyKey,
        String title,
        MemberId applicantMemberId,
        ApprovalInstanceStatus status,
        Integer currentStepOrder,
        Map<FieldCode, List<String>> facts,
        Long version,
        LocalDateTime submittedTime,
        LocalDateTime completedTime,
        LocalDateTime updatedTime) {

    public ApprovalInstanceSummary {
        if (id == null || definitionId == null || definitionVersion == null
                || objectType == null || subjectId == null || subjectId <= 0
                || idempotencyKey == null || title == null || applicantMemberId == null
                || status == null || version == null) {
            throw new IllegalArgumentException("approval instance summary is invalid");
        }
        LinkedHashMap<FieldCode, List<String>> copied = new LinkedHashMap<>();
        (facts == null ? Map.<FieldCode, List<String>>of() : facts).forEach(
                (code, values) -> copied.put(code, List.copyOf(values)));
        facts = java.util.Collections.unmodifiableMap(copied);
    }

    public static ApprovalInstanceSummary from(ApprovalInstance instance) {
        if (instance == null || instance.getId() == null) {
            throw new IllegalArgumentException("persisted approval instance is required");
        }
        return new ApprovalInstanceSummary(instance.getId(), instance.getDefinitionId(),
                instance.getDefinitionVersion(), instance.getObjectType(),
                instance.getSubjectId(), instance.getIdempotencyKey(), instance.getTitle(),
                instance.getApplicantMemberId(), instance.getStatus(),
                instance.getCurrentStepOrder(), instance.getFacts(), instance.getVersion(),
                instance.getSubmittedTime(), instance.getCompletedTime(),
                instance.getUpdatedTime());
    }
}
