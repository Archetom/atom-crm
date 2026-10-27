package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.archetom.crm.domain.entity.ActivityOption;
import io.github.archetom.crm.domain.model.ActivityOptionType;
import io.github.archetom.crm.domain.repository.ActivityOptionRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ActivityOptionMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.ActivityOptionPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ActivityOptionRepositoryImpl implements ActivityOptionRepository {
    private final ActivityOptionMapper mapper;
    private final OrganizationTenantRepository tenantRepository;

    @Override
    public List<ActivityOption> findAll(TenantId tenantId) {
        requireTenant(tenantId);
        return mapper.selectList(new LambdaQueryWrapper<ActivityOptionPO>()
                        .eq(ActivityOptionPO::getTenantId, tenantId.getValue())
                        .orderByAsc(ActivityOptionPO::getOptionType, ActivityOptionPO::getDisplayOrder,
                                ActivityOptionPO::getOptionValue)).stream()
                .map(value -> new ActivityOption(ActivityOptionType.valueOf(value.getOptionType()),
                        value.getOptionValue(), value.getLabel(), value.getDisplayOrder())).toList();
    }

    @Override
    public List<ActivityOption> lockAndFindAll(TenantId tenantId) {
        requireTenant(tenantId);
        lockTenant(tenantId);
        return findAll(tenantId);
    }

    @Override
    public void replaceAll(TenantId tenantId, Collection<ActivityOption> options) {
        requireTenant(tenantId);
        if (options == null || options.isEmpty() || options.size() > 100) {
            throw new IllegalArgumentException("activity options are invalid");
        }
        lockTenant(tenantId);
        mapper.delete(new LambdaQueryWrapper<ActivityOptionPO>()
                .eq(ActivityOptionPO::getTenantId, tenantId.getValue()));
        for (ActivityOption option : options) {
            ActivityOptionPO po = new ActivityOptionPO();
            po.setTenantId(tenantId.getValue());
            po.setOptionType(option.type().name());
            po.setOptionValue(option.value());
            po.setLabel(option.label());
            po.setDisplayOrder(option.displayOrder());
            if (mapper.insert(po) != 1) throw new IllegalStateException("activity option insert failed");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("activity option tenant is required");
    }

    private void lockTenant(TenantId tenantId) {
        if (!tenantRepository.lockForTenantChanges(tenantId)) {
            throw new IllegalStateException("activity option tenant is unavailable");
        }
    }
}
