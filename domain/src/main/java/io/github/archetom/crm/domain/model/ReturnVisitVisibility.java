package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ReturnVisitDomainException;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;

import java.util.Set;

public record ReturnVisitVisibility(DataScope dataScope, MemberId callerMemberId,
                                    Set<DepartmentId> ownerDepartmentIds,
                                    ReturnVisitAccessMode accessMode) {
    public ReturnVisitVisibility {
        if (dataScope == null || callerMemberId == null || accessMode == null) {
            throw new ReturnVisitDomainException("return visit visibility is invalid");
        }
        ownerDepartmentIds = ownerDepartmentIds == null ? Set.of() : Set.copyOf(ownerDepartmentIds);
        if ((dataScope == DataScope.DEPARTMENT || dataScope == DataScope.DEPARTMENT_SUBTREE)
                && ownerDepartmentIds.isEmpty()) {
            throw new ReturnVisitDomainException("department return visit visibility requires departments");
        }
    }
}
