package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.LeadDomainException;
import io.github.archetom.crm.domain.model.LeadCollaboratorAccess;
import io.github.archetom.crm.domain.valueobject.MemberId;

/** Explicit record-level collaboration owned by a lead. */
public record LeadCollaborator(MemberId memberId, LeadCollaboratorAccess access) {

    public LeadCollaborator {
        if (memberId == null || access == null) {
            throw new LeadDomainException("lead collaborator is invalid");
        }
    }
}
