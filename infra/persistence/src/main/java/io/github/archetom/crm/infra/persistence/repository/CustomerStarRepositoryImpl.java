package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.archetom.crm.domain.repository.CustomerStarRepository;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomerStarMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerStarPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CustomerStarRepositoryImpl implements CustomerStarRepository {

    private final CustomerStarMapper mapper;

    @Override
    public void setStarred(
            TenantId tenantId, MemberId memberId, CustomerId customerId, boolean starred) {
        require(tenantId, memberId, customerId);
        if (!starred) {
            mapper.delete(filter(tenantId, memberId).eq(
                    CustomerStarPO::getCustomerId, customerId.getValue()));
            return;
        }
        CustomerStarPO po = new CustomerStarPO();
        po.setTenantId(tenantId.getValue());
        po.setActorId(memberId.getValue());
        po.setCustomerId(customerId.getValue());
        po.setCreatedTime(LocalDateTime.now());
        try {
            mapper.insert(po);
        } catch (DuplicateKeyException ignored) {
            // Idempotent personal preference update.
        }
    }

    @Override
    public boolean isStarred(TenantId tenantId, MemberId memberId, CustomerId customerId) {
        require(tenantId, memberId, customerId);
        return mapper.selectCount(filter(tenantId, memberId)
                .eq(CustomerStarPO::getCustomerId, customerId.getValue())) > 0;
    }

    @Override
    public Set<CustomerId> findStarred(
            TenantId tenantId, MemberId memberId, Collection<CustomerId> customerIds) {
        if (tenantId == null || memberId == null) {
            throw new IllegalArgumentException("tenant and member must not be null");
        }
        List<Long> ids = customerIds == null ? List.of() : customerIds.stream()
                .filter(java.util.Objects::nonNull).map(CustomerId::getValue).distinct().toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        return mapper.selectList(filter(tenantId, memberId)
                        .in(CustomerStarPO::getCustomerId, ids))
                .stream().map(value -> new CustomerId(value.getCustomerId()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private LambdaQueryWrapper<CustomerStarPO> filter(TenantId tenantId, MemberId memberId) {
        return new LambdaQueryWrapper<CustomerStarPO>()
                .eq(CustomerStarPO::getTenantId, tenantId.getValue())
                .eq(CustomerStarPO::getActorId, memberId.getValue());
    }

    private void require(TenantId tenantId, MemberId memberId, CustomerId customerId) {
        if (tenantId == null || memberId == null || customerId == null) {
            throw new IllegalArgumentException("tenant, member, and customer must not be null");
        }
    }
}
