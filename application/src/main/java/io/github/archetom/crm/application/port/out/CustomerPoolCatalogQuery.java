package io.github.archetom.crm.application.port.out;

import io.github.archetom.crm.domain.model.CustomerPoolStatus;
import io.github.archetom.crm.domain.valueobject.CustomerPoolId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;

/** Lightweight customer-pool catalog read contract without aggregate configuration relations. */
public interface CustomerPoolCatalogQuery {

    List<Entry> findAccessible(TenantId tenantId, MemberId memberId,
                               DepartmentId departmentId, boolean tenantWide);

    record Entry(CustomerPoolId id, String name, CustomerPoolStatus status,
                 boolean administrator) {
    }
}
