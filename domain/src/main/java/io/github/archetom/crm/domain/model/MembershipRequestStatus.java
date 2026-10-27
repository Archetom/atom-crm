package io.github.archetom.crm.domain.model;

/** State machine for a request to join a tenant. */
public enum MembershipRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    WITHDRAWN
}
