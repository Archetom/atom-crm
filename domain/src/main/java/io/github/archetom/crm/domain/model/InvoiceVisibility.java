package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.InvoiceDomainException;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;

import java.util.Set;

public record InvoiceVisibility(
        DataScope dataScope,
        MemberId callerMemberId,
        Set<DepartmentId> ownerDepartmentIds,
        InvoiceAccessMode accessMode) {
    public InvoiceVisibility {
        if (dataScope == null || callerMemberId == null || accessMode == null) {
            throw new InvoiceDomainException("invoice visibility is invalid");
        }
        ownerDepartmentIds = ownerDepartmentIds == null ? Set.of() : Set.copyOf(ownerDepartmentIds);
        if ((dataScope == DataScope.DEPARTMENT || dataScope == DataScope.DEPARTMENT_SUBTREE)
                && ownerDepartmentIds.isEmpty()) {
            throw new InvoiceDomainException("department invoice visibility requires departments");
        }
    }
}
