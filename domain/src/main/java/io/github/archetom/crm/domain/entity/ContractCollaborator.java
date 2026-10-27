package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.ContractDomainException;
import io.github.archetom.crm.domain.model.ContractCollaboratorAccess;
import io.github.archetom.crm.domain.valueobject.MemberId;

import java.time.LocalDate;

/** A contract team member with an optional inclusive access expiry date. */
public record ContractCollaborator(
        MemberId memberId, ContractCollaboratorAccess access, LocalDate effectiveUntil) {
    public ContractCollaborator {
        if (memberId == null || access == null) {
            throw new ContractDomainException("contract collaborator is invalid");
        }
        if (effectiveUntil != null && (effectiveUntil.getYear() < 1000
                || effectiveUntil.getYear() > 9999)) {
            throw new ContractDomainException("contract collaborator effective-until is invalid");
        }
    }

    public boolean isEffectiveOn(LocalDate date) {
        if (date == null) {
            throw new ContractDomainException("contract collaborator effective date is required");
        }
        return effectiveUntil == null || !effectiveUntil.isBefore(date);
    }
}
