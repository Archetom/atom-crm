package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ContractDomainException;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;

import java.util.Set;

public record ContractVisibility(
        DataScope dataScope,
        MemberId callerMemberId,
        Set<DepartmentId> ownerDepartmentIds,
        ContractAccessMode accessMode) {
    public ContractVisibility {
        if (dataScope == null || callerMemberId == null || accessMode == null) {
            throw new ContractDomainException("contract visibility is invalid");
        }
        ownerDepartmentIds = ownerDepartmentIds == null ? Set.of() : Set.copyOf(ownerDepartmentIds);
        if ((dataScope == DataScope.DEPARTMENT || dataScope == DataScope.DEPARTMENT_SUBTREE)
                && ownerDepartmentIds.isEmpty()) {
            throw new ContractDomainException("department contract visibility requires departments");
        }
    }
}
