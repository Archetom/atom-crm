package io.github.archetom.crm.api.dto.response;

/** A visible, currently active transfer target; revalidated when submitted. */
public record ApprovalTransferCandidateResponse(Long actorId, String displayName) {
}
