package io.github.archetom.crm.application.vo;

/** Candidate returned only for a writable specialized field on an available OA form. */
public record OaFormFieldCandidateVO(Long id, String displayName, String valueType) {
}
