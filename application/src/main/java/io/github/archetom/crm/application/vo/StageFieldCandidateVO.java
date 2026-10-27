package io.github.archetom.crm.application.vo;

/** Candidate returned only after verifying the workflow record, current stage and field. */
public record StageFieldCandidateVO(Long id, String displayName, String valueType) {
}
