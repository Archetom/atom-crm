package io.github.archetom.crm.application.vo;

/** Safe confirmation for an anonymous idempotent submission. */
public record MarketingFormSubmissionVO(Long submissionId, boolean accepted, String idempotencyKey) {
}
