package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.ConnectorRegistration;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.ConnectorAlreadyExistsException;
import io.github.archetom.crm.domain.model.ConnectorQuery;
import io.github.archetom.crm.domain.model.ConnectorStatus;
import io.github.archetom.crm.domain.repository.ConnectorRegistrationRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.ConnectorId;
import io.github.archetom.crm.domain.valueobject.ConnectorRef;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.ConnectorRegistrationPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ConnectorCredentialMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ConnectorRegistrationMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.ConnectorCredentialPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ConnectorRegistrationPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ConnectorRegistrationRepositoryImpl implements ConnectorRegistrationRepository {
    private final ConnectorRegistrationMapper registrationMapper;
    private final ConnectorCredentialMapper credentialMapper;
    private final ConnectorRegistrationPOConverter converter;

    @Override
    public ConnectorRegistration save(
            TenantId tenantId, ConnectorRegistration registration) {
        requireTenantMatch(tenantId, registration);
        ConnectorRegistrationPO po = converter.toPO(registration);
        try {
            if (po.getId() == null) {
                if (registrationMapper.insert(po) != 1) {
                    throw new IllegalStateException(
                            "Connector registration insert did not affect exactly one row");
                }
            } else {
                Long expectedVersion = po.getVersion();
                if (registrationMapper.update(po,
                        new LambdaUpdateWrapper<ConnectorRegistrationPO>()
                                .eq(ConnectorRegistrationPO::getTenantId, tenantId.getValue())
                                .eq(ConnectorRegistrationPO::getId, po.getId())
                                .eq(ConnectorRegistrationPO::getVersion, expectedVersion)) != 1) {
                    throw new AggregateVersionConflictException();
                }
            }
            ConnectorId connectorId = new ConnectorId(po.getId());
            synchronizeCredentials(tenantId, connectorId, registration);
            registration.onPersisted(connectorId, po.getVersion(),
                    po.getCreatedTime(), po.getUpdatedTime());
            return registration;
        } catch (DuplicateKeyException exception) {
            throw new ConnectorAlreadyExistsException();
        }
    }

    @Override
    public Optional<ConnectorRegistration> findById(
            TenantId tenantId, ConnectorId connectorId) {
        requireTenant(tenantId);
        if (connectorId == null) {
            return Optional.empty();
        }
        return hydrate(tenantId, registrationMapper.selectOne(rootFilter(tenantId)
                .eq(ConnectorRegistrationPO::getId, connectorId.getValue())));
    }

    @Override
    public Optional<ConnectorRegistration> findByIdForUpdate(
            TenantId tenantId, ConnectorId connectorId) {
        requireTenant(tenantId);
        if (connectorId == null) {
            return Optional.empty();
        }
        return hydrate(tenantId, registrationMapper.selectOne(rootFilter(tenantId)
                .eq(ConnectorRegistrationPO::getId, connectorId.getValue())
                .last("FOR UPDATE")));
    }

    @Override
    public PageResult<ConnectorRegistration> findPage(
            TenantId tenantId, ConnectorQuery query) {
        requireTenant(tenantId);
        if (query == null) {
            throw new IllegalArgumentException("connector query is required");
        }
        var filter = rootFilter(tenantId);
        if (query.status() != null) {
            filter.eq(ConnectorRegistrationPO::getStatus, query.status().name());
        }
        filter.orderByDesc(ConnectorRegistrationPO::getUpdatedTime)
                .orderByDesc(ConnectorRegistrationPO::getId);
        IPage<ConnectorRegistrationPO> page = registrationMapper.selectPage(
                new Page<>(query.page(), query.size()), filter);
        return new PageResult<>(page.getCurrent(), page.getSize(), Math.max(0, page.getTotal()),
                hydrate(tenantId, page.getRecords()));
    }

    @Override
    public boolean lockActive(
            TenantId tenantId, ConnectorId connectorId, ConnectorRef connectorRef) {
        requireTenant(tenantId);
        if (connectorId == null || connectorRef == null) {
            return false;
        }
        ConnectorRegistrationPO locked = registrationMapper.selectOne(rootFilter(tenantId)
                .eq(ConnectorRegistrationPO::getId, connectorId.getValue())
                .eq(ConnectorRegistrationPO::getConnectorRef, connectorRef.getValue())
                .eq(ConnectorRegistrationPO::getStatus, ConnectorStatus.ACTIVE.name())
                .last("FOR UPDATE"));
        return locked != null;
    }

    private Optional<ConnectorRegistration> hydrate(
            TenantId tenantId, ConnectorRegistrationPO root) {
        if (root == null) {
            return Optional.empty();
        }
        List<ConnectorCredentialPO> credentials = credentials(
                tenantId, List.of(root.getId()));
        return Optional.of(converter.toDomain(root, credentials));
    }

    private List<ConnectorRegistration> hydrate(
            TenantId tenantId, Collection<ConnectorRegistrationPO> roots) {
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ConnectorCredentialPO>> byConnector = credentials(tenantId,
                roots.stream().map(ConnectorRegistrationPO::getId).toList()).stream()
                .collect(Collectors.groupingBy(ConnectorCredentialPO::getConnectorId,
                        LinkedHashMap::new, Collectors.toList()));
        return roots.stream().map(root -> converter.toDomain(root,
                byConnector.getOrDefault(root.getId(), List.of()))).toList();
    }

    private List<ConnectorCredentialPO> credentials(
            TenantId tenantId, Collection<Long> connectorIds) {
        if (connectorIds == null || connectorIds.isEmpty()) {
            return List.of();
        }
        return credentialMapper.selectList(new LambdaQueryWrapper<ConnectorCredentialPO>()
                .eq(ConnectorCredentialPO::getTenantId, tenantId.getValue())
                .in(ConnectorCredentialPO::getConnectorId, connectorIds)
                .orderByAsc(ConnectorCredentialPO::getCreatedTime)
                .orderByAsc(ConnectorCredentialPO::getKeyId));
    }

    private void synchronizeCredentials(
            TenantId tenantId, ConnectorId connectorId, ConnectorRegistration registration) {
        credentialMapper.delete(new LambdaQueryWrapper<ConnectorCredentialPO>()
                .eq(ConnectorCredentialPO::getTenantId, tenantId.getValue())
                .eq(ConnectorCredentialPO::getConnectorId, connectorId.getValue()));
        for (var credential : registration.getCredentialKeys()) {
            if (credentialMapper.insert(converter.toPO(
                    tenantId, connectorId, registration.getConnectorRef(), credential)) != 1) {
                throw new IllegalStateException(
                        "Connector credential insert did not affect exactly one row");
            }
        }
    }

    private LambdaQueryWrapper<ConnectorRegistrationPO> rootFilter(TenantId tenantId) {
        requireTenant(tenantId);
        return new LambdaQueryWrapper<ConnectorRegistrationPO>()
                .eq(ConnectorRegistrationPO::getTenantId, tenantId.getValue());
    }

    private void requireTenantMatch(
            TenantId tenantId, ConnectorRegistration registration) {
        requireTenant(tenantId);
        if (registration == null || registration.getTenantId() == null
                || !tenantId.sameValueAs(registration.getTenantId())) {
            throw new IllegalArgumentException(
                    "connector registration tenant does not match repository tenant");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID must not be null");
        }
    }
}
