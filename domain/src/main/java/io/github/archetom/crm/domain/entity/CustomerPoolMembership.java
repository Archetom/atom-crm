package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.CustomerPoolDomainException;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.CustomerPoolId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.LocalDateTime;

/** Current placement of one customer in exactly one pool. */
public record CustomerPoolMembership(
        TenantId tenantId,
        CustomerPoolId poolId,
        CustomerId customerId,
        MemberId previousOwnerMemberId,
        LocalDateTime enteredTime) {

    public CustomerPoolMembership {
        if (tenantId == null || poolId == null || customerId == null || enteredTime == null) {
            throw new CustomerPoolDomainException("customer pool membership is invalid");
        }
    }
}
