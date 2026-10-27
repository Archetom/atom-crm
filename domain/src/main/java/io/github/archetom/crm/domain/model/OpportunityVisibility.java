package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.OpportunityDomainException;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;

import java.util.Set;

/** Fully resolved record predicate derived from verified membership and one authority grant. */
public record OpportunityVisibility(
        DataScope dataScope,
        MemberId callerMemberId,
        Set<DepartmentId> ownerDepartmentIds,
        OpportunityAccessMode accessMode) {

    public OpportunityVisibility {
        if (dataScope == null || callerMemberId == null || accessMode == null) {
            throw new OpportunityDomainException("opportunity visibility is invalid");
        }
        ownerDepartmentIds = ownerDepartmentIds == null ? Set.of() : Set.copyOf(ownerDepartmentIds);
        if ((dataScope == DataScope.DEPARTMENT || dataScope == DataScope.DEPARTMENT_SUBTREE)
                && ownerDepartmentIds.isEmpty()) {
            throw new OpportunityDomainException(
                    "department opportunity visibility requires departments");
        }
    }
}
