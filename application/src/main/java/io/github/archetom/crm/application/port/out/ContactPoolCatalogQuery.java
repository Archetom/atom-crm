package io.github.archetom.crm.application.port.out;

import io.github.archetom.crm.domain.model.ContactPoolStatus;
import io.github.archetom.crm.domain.valueobject.ContactPoolId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;

/** Lightweight contact-pool catalog read contract without aggregate configuration relations. */
public interface ContactPoolCatalogQuery {

    List<Entry> findAccessible(TenantId tenantId, MemberId memberId,
                               DepartmentId departmentId, boolean tenantWide);

    record Entry(ContactPoolId id, String name, ContactPoolStatus status,
                 boolean administrator) {
    }
}
