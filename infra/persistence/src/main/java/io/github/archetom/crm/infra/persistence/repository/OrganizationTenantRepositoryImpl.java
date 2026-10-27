package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OrganizationTenantMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.OrganizationTenantPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** Uses the tenant primary key as an atomic, database-enforced initialization marker. */
@Repository
@RequiredArgsConstructor
public class OrganizationTenantRepositoryImpl implements OrganizationTenantRepository {

    private final OrganizationTenantMapper organizationTenantMapper;

    @Override
    public boolean initialize(TenantId tenantId, MemberId initializedBy) {
        require(tenantId, initializedBy);
        OrganizationTenantPO po = new OrganizationTenantPO();
        po.setTenantId(tenantId.getValue());
        po.setInitializedByActorId(initializedBy.getValue());
        try {
            return organizationTenantMapper.insert(po) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public boolean exists(TenantId tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("tenant ID must not be null");
        return organizationTenantMapper.selectById(tenantId.getValue()) != null;
    }

    @Override
    public boolean lockForStructureChanges(TenantId tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("tenant ID must not be null");
        return organizationTenantMapper.selectOne(new LambdaQueryWrapper<OrganizationTenantPO>()
                .eq(OrganizationTenantPO::getTenantId, tenantId.getValue())
                .last("FOR UPDATE")) != null;
    }

    private void require(TenantId tenantId, MemberId initializedBy) {
        if (tenantId == null || initializedBy == null) {
            throw new IllegalArgumentException("tenant ID and initializer must not be null");
        }
    }
}
