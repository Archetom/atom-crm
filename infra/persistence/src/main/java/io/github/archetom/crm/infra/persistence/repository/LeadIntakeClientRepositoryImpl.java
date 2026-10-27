package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.archetom.crm.domain.entity.LeadIntakeClient;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.LeadDomainException;
import io.github.archetom.crm.domain.exception.LeadIntakeClientAlreadyExistsException;
import io.github.archetom.crm.domain.repository.LeadIntakeClientRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.valueobject.LeadIntakeClientId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.LeadIntakeClientPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.LeadIntakeClientMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadIntakeClientPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LeadIntakeClientRepositoryImpl implements LeadIntakeClientRepository {

    private final LeadIntakeClientMapper mapper;
    private final LeadIntakeClientPOConverter converter;
    private final OrganizationTenantRepository organizationTenantRepository;

    @Override
    public LeadIntakeClient save(TenantId tenantId, LeadIntakeClient client) {
        if (tenantId == null || client == null || !tenantId.equals(client.getTenantId())) {
            throw new IllegalArgumentException("lead intake client tenant is invalid");
        }
        LeadIntakeClientPO po = converter.toPO(client);
        try {
            if (po.getId() == null) {
                requireCreationCapacity(tenantId);
                if (mapper.insert(po) != 1) {
                    throw new IllegalStateException("lead intake client insert failed");
                }
            } else if (mapper.update(po, new LambdaUpdateWrapper<LeadIntakeClientPO>()
                    .eq(LeadIntakeClientPO::getTenantId, tenantId.getValue())
                    .eq(LeadIntakeClientPO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
            client.onPersisted(new LeadIntakeClientId(po.getId()), po.getVersion(),
                    po.getCreatedTime(), po.getUpdatedTime());
            return client;
        } catch (DuplicateKeyException exception) {
            throw new LeadIntakeClientAlreadyExistsException();
        }
    }

    @Override
    public List<LeadIntakeClient> findAll(TenantId tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<LeadIntakeClientPO>()
                        .eq(LeadIntakeClientPO::getTenantId, tenantId.getValue())
                        .orderByDesc(LeadIntakeClientPO::getId))
                .stream().map(converter::toDomain).toList();
    }

    @Override
    public Optional<LeadIntakeClient> findById(TenantId tenantId, LeadIntakeClientId clientId) {
        if (tenantId == null || clientId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<LeadIntakeClientPO>()
                        .eq(LeadIntakeClientPO::getTenantId, tenantId.getValue())
                        .eq(LeadIntakeClientPO::getId, clientId.getValue())))
                .map(converter::toDomain);
    }

    @Override
    public Optional<LeadIntakeClient> findActiveByKeyId(TenantId tenantId, String keyId) {
        if (tenantId == null || keyId == null || keyId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<LeadIntakeClientPO>()
                        .eq(LeadIntakeClientPO::getTenantId, tenantId.getValue())
                        .eq(LeadIntakeClientPO::getKeyId, keyId)
                        .eq(LeadIntakeClientPO::getActive, true)))
                .map(converter::toDomain);
    }

    private void requireCreationCapacity(TenantId tenantId) {
        if (!organizationTenantRepository.lockForTenantChanges(tenantId)) {
            throw new LeadDomainException("lead intake client tenant is not initialized");
        }
        long activeClients = mapper.selectCount(new LambdaQueryWrapper<LeadIntakeClientPO>()
                .eq(LeadIntakeClientPO::getTenantId, tenantId.getValue())
                .eq(LeadIntakeClientPO::getActive, true));
        if (activeClients >= LeadIntakeClient.MAX_ACTIVE_CLIENTS_PER_TENANT) {
            throw new LeadDomainException("lead intake client tenant limit was reached");
        }
    }
}
