package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.CustomerDomainException;
import io.github.archetom.crm.domain.model.CustomerCollaboratorAccess;
import io.github.archetom.crm.domain.valueobject.MemberId;

/** Explicit record-level collaboration owned by a customer. */
public record CustomerCollaborator(MemberId memberId, CustomerCollaboratorAccess access) {

    public CustomerCollaborator {
        if (memberId == null || access == null) {
            throw new CustomerDomainException("customer collaborator is invalid");
        }
    }
}
