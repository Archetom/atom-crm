package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ReceivableDomainException;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;

import java.util.Set;

public record ReceivableVisibility(
        DataScope dataScope, MemberId callerMemberId,
        Set<DepartmentId> ownerDepartmentIds, ReceivableAccessMode accessMode) {
    public ReceivableVisibility {
        if (dataScope == null || callerMemberId == null || accessMode == null) {
            throw new ReceivableDomainException("receivable visibility is invalid");
        }
        ownerDepartmentIds = ownerDepartmentIds == null ? Set.of() : Set.copyOf(ownerDepartmentIds);
        if ((dataScope == DataScope.DEPARTMENT || dataScope == DataScope.DEPARTMENT_SUBTREE)
                && ownerDepartmentIds.isEmpty()) {
            throw new ReceivableDomainException(
                    "department receivable visibility requires departments");
        }
    }
}
