package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.ContactDomainException;
import io.github.archetom.crm.domain.model.ContactCollaboratorAccess;
import io.github.archetom.crm.domain.valueobject.MemberId;

/** Explicit record-level collaboration owned by a contact. */
public record ContactCollaborator(MemberId memberId, ContactCollaboratorAccess access) {

    public ContactCollaborator {
        if (memberId == null || access == null) {
            throw new ContactDomainException("contact collaborator is invalid");
        }
    }
}
