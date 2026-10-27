package io.github.archetom.crm.api.dto.request;

/** The only decisions a pending approval task accepts. */
public enum ApprovalAction {
    APPROVE,
    REJECT,
    TRANSFER
}
