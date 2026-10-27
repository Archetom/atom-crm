package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.ContactPoolDomainException;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.ContactPoolId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.LocalDateTime;

/** Current placement of one contact in exactly one pool. */
public record ContactPoolMembership(
        TenantId tenantId,
        ContactPoolId poolId,
        ContactId contactId,
        MemberId previousOwnerMemberId,
        LocalDateTime enteredTime) {

    public ContactPoolMembership {
        if (tenantId == null || poolId == null || contactId == null || enteredTime == null) {
            throw new ContactPoolDomainException("contact pool membership is invalid");
        }
    }
}
