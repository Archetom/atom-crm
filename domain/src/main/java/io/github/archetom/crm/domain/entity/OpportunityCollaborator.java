package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.OpportunityDomainException;
import io.github.archetom.crm.domain.model.OpportunityCollaboratorAccess;
import io.github.archetom.crm.domain.valueobject.MemberId;

/** Explicit record-level collaboration owned by an opportunity. */
public record OpportunityCollaborator(
        MemberId memberId, OpportunityCollaboratorAccess access) {

    public OpportunityCollaborator {
        if (memberId == null || access == null) {
            throw new OpportunityDomainException("opportunity collaborator is invalid");
        }
    }
}
