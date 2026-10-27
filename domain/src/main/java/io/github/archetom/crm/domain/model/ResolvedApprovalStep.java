package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ApprovalDomainException;
import io.github.archetom.crm.domain.valueobject.MemberId;

import java.util.LinkedHashSet;
import java.util.List;

/** Submission-time route snapshot after conditions and assignments are resolved. */
public record ResolvedApprovalStep(
        String code,
        String name,
        int order,
        ApprovalStepMode mode,
        List<MemberId> assignees) {

    public ResolvedApprovalStep {
        if (code == null || code.isBlank() || name == null || name.isBlank()
                || order < 0 || mode == null) {
            throw new ApprovalDomainException("resolved approval step is invalid");
        }
        assignees = assignees == null ? List.of() : List.copyOf(assignees);
        if (assignees.isEmpty() || assignees.size() > ApprovalStepDefinition.MAX_ACTORS
                || assignees.stream().anyMatch(java.util.Objects::isNull)
                || new LinkedHashSet<>(assignees).size() != assignees.size()) {
            throw new ApprovalDomainException("resolved approval assignees are invalid");
        }
    }
}
