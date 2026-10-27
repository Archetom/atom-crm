package io.github.archetom.crm.domain.model;

/** The latest approval state projected onto an already-authorized business subject. */
public record ApprovalSubjectStatus(Long subjectId, ApprovalInstanceStatus status) {
    public ApprovalSubjectStatus {
        if (subjectId == null || subjectId <= 0) {
            throw new IllegalArgumentException("approval subject ID is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("approval status is required");
        }
    }
}
