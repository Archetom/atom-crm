package io.github.archetom.crm.infra.persistence.query;

import io.github.archetom.crm.application.port.out.CustomerPoolCatalogQuery;
import io.github.archetom.crm.domain.entity.CustomerPool;
import io.github.archetom.crm.domain.exception.CustomerPoolDomainException;
import io.github.archetom.crm.domain.model.CustomerPoolStatus;
import io.github.archetom.crm.domain.valueobject.CustomerPoolId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomerPoolMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPoolCatalogPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MySqlCustomerPoolCatalogQuery implements CustomerPoolCatalogQuery {

    private final CustomerPoolMapper poolMapper;

    @Override
    public List<Entry> findAccessible(
            TenantId tenantId, MemberId memberId, DepartmentId departmentId,
            boolean tenantWide) {
        if (tenantId == null) throw new IllegalArgumentException("tenant ID must not be null");
        if (memberId == null) throw new IllegalArgumentException("member ID must not be null");
        List<CustomerPoolCatalogPO> rows = poolMapper.selectCatalogEntries(
                tenantId.getValue(), memberId.getValue(),
                departmentId == null ? null : departmentId.getValue(), tenantWide,
                CustomerPool.MAX_POOLS_PER_TENANT + 1);
        if (rows.size() > CustomerPool.MAX_POOLS_PER_TENANT) {
            throw new CustomerPoolDomainException("customer pool tenant limit was exceeded");
        }
        return rows.stream().map(row -> new Entry(
                new CustomerPoolId(row.getId()), row.getName(),
                CustomerPoolStatus.valueOf(row.getStatus()),
                Boolean.TRUE.equals(row.getAdministrator()))).toList();
    }
}
