package io.github.archetom.crm.api.dto.response;

/** Common post-mutation projection for approval actions, withdrawal, and cancellation. */
public record ApprovalActionResult(ApprovalInstanceResponse instance) {
}
