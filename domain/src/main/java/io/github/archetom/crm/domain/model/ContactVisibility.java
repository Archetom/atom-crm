package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ContactDomainException;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;

import java.util.Set;

/** Fully resolved record predicate derived from verified membership and one authority grant. */
public record ContactVisibility(
        DataScope dataScope,
        MemberId callerMemberId,
        Set<DepartmentId> ownerDepartmentIds,
        ContactAccessMode accessMode) {

    public ContactVisibility {
        if (dataScope == null || callerMemberId == null || accessMode == null) {
            throw new ContactDomainException("contact visibility is invalid");
        }
        ownerDepartmentIds = ownerDepartmentIds == null ? Set.of() : Set.copyOf(ownerDepartmentIds);
        if ((dataScope == DataScope.DEPARTMENT || dataScope == DataScope.DEPARTMENT_SUBTREE)
                && ownerDepartmentIds.isEmpty()) {
            throw new ContactDomainException("department contact visibility requires departments");
        }
    }
}
