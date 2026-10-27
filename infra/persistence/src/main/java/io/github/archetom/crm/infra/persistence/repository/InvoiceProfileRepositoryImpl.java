package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.archetom.crm.domain.entity.InvoiceProfile;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.model.InvoiceProfileStatus;
import io.github.archetom.crm.domain.repository.InvoiceProfileRepository;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.InvoiceProfileId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.InvoiceProfilePOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.InvoiceProfileMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.InvoiceProfilePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class InvoiceProfileRepositoryImpl implements InvoiceProfileRepository {
    private final InvoiceProfileMapper mapper;
    private final InvoiceProfilePOConverter converter;

    @Override
    public InvoiceProfile save(TenantId tenantId, InvoiceProfile profile) {
        if (tenantId == null || profile == null || profile.getTenantId() == null
                || !tenantId.sameValueAs(profile.getTenantId())) {
            throw new IllegalArgumentException("invoice profile tenant does not match");
        }
        InvoiceProfilePO po = converter.toPO(profile);
        if (po.getId() == null) {
            if (mapper.insert(po) != 1) {
                throw new IllegalStateException("Invoice profile insert did not affect one row");
            }
        } else if (mapper.update(po, new LambdaUpdateWrapper<InvoiceProfilePO>()
                .eq(InvoiceProfilePO::getTenantId, tenantId.getValue())
                .eq(InvoiceProfilePO::getId, po.getId())) != 1) {
            throw new AggregateVersionConflictException();
        }
        profile.onPersisted(new InvoiceProfileId(po.getId()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime());
        return profile;
    }

    @Override
    public Optional<InvoiceProfile> findById(
            TenantId tenantId, InvoiceProfileId profileId) {
        requireTenant(tenantId);
        if (profileId == null) {
            return Optional.empty();
        }
        InvoiceProfilePO po = mapper.selectOne(new LambdaQueryWrapper<InvoiceProfilePO>()
                .eq(InvoiceProfilePO::getTenantId, tenantId.getValue())
                .eq(InvoiceProfilePO::getId, profileId.getValue())
                .eq(InvoiceProfilePO::getStatus, InvoiceProfileStatus.ACTIVE.name()));
        return Optional.ofNullable(po).map(converter::toDomain);
    }

    @Override
    public List<InvoiceProfile> findByCustomer(
            TenantId tenantId, CustomerId customerId, int limit) {
        requireTenant(tenantId);
        if (limit < 1 || limit > InvoiceProfile.MAX_ACTIVE_PER_CUSTOMER) {
            throw new IllegalArgumentException("invoice profile limit is invalid");
        }
        if (customerId == null) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<InvoiceProfilePO>()
                .eq(InvoiceProfilePO::getTenantId, tenantId.getValue())
                .eq(InvoiceProfilePO::getCustomerId, customerId.getValue())
                .eq(InvoiceProfilePO::getStatus, InvoiceProfileStatus.ACTIVE.name())
                .orderByAsc(InvoiceProfilePO::getId)
                .last("LIMIT " + limit)).stream()
                .map(converter::toDomain).toList();
    }

    @Override
    public long countActiveByCustomer(TenantId tenantId, CustomerId customerId) {
        requireTenant(tenantId);
        if (customerId == null) {
            return 0L;
        }
        return mapper.selectCount(new LambdaQueryWrapper<InvoiceProfilePO>()
                .eq(InvoiceProfilePO::getTenantId, tenantId.getValue())
                .eq(InvoiceProfilePO::getCustomerId, customerId.getValue())
                .eq(InvoiceProfilePO::getStatus, InvoiceProfileStatus.ACTIVE.name()));
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant is required");
        }
    }
}
