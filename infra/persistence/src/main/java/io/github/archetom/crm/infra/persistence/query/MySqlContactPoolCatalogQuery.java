package io.github.archetom.crm.infra.persistence.query;

import io.github.archetom.crm.application.port.out.ContactPoolCatalogQuery;
import io.github.archetom.crm.domain.entity.ContactPool;
import io.github.archetom.crm.domain.exception.ContactPoolDomainException;
import io.github.archetom.crm.domain.model.ContactPoolStatus;
import io.github.archetom.crm.domain.valueobject.ContactPoolId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContactPoolMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactPoolCatalogPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MySqlContactPoolCatalogQuery implements ContactPoolCatalogQuery {

    private final ContactPoolMapper poolMapper;

    @Override
    public List<Entry> findAccessible(
            TenantId tenantId, MemberId memberId, DepartmentId departmentId,
            boolean tenantWide) {
        if (tenantId == null) throw new IllegalArgumentException("tenant ID must not be null");
        if (memberId == null) throw new IllegalArgumentException("member ID must not be null");
        List<ContactPoolCatalogPO> rows = poolMapper.selectCatalogEntries(
                tenantId.getValue(), memberId.getValue(),
                departmentId == null ? null : departmentId.getValue(), tenantWide,
                ContactPool.MAX_POOLS_PER_TENANT + 1);
        if (rows.size() > ContactPool.MAX_POOLS_PER_TENANT) {
            throw new ContactPoolDomainException("contact pool tenant limit was exceeded");
        }
        return rows.stream().map(row -> new Entry(
                new ContactPoolId(row.getId()), row.getName(),
                ContactPoolStatus.valueOf(row.getStatus()),
                Boolean.TRUE.equals(row.getAdministrator()))).toList();
    }
}
