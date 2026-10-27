package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.ApprovalDefinition;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.ApprovalAlreadyExistsException;
import io.github.archetom.crm.domain.model.ApprovalDefinitionQuery;
import io.github.archetom.crm.domain.model.ApprovalDefinitionStatus;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.repository.ApprovalDefinitionRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.ApprovalDefinitionPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ApprovalDefinitionMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.ApprovalDefinitionPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ApprovalDefinitionRepositoryImpl implements ApprovalDefinitionRepository {

    private final ApprovalDefinitionMapper mapper;
    private final ApprovalDefinitionPOConverter converter;

    @Override
    public ApprovalDefinition save(TenantId tenantId, ApprovalDefinition definition) {
        requireTenantMatch(tenantId, definition);
        ApprovalDefinitionPO po = converter.toPO(definition);
        try {
            if (po.getId() == null) {
                if (mapper.insert(po) != 1) {
                    throw new IllegalStateException("Approval definition insert did not affect one row");
                }
            } else if (mapper.update(po, tenantFilter(tenantId)
                    .eq(ApprovalDefinitionPO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
        } catch (DuplicateKeyException exception) {
            throw new ApprovalAlreadyExistsException();
        }
        definition.onPersisted(new ApprovalDefinitionId(po.getId()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime());
        return definition;
    }

    @Override
    public Optional<ApprovalDefinition> findById(
            TenantId tenantId, ApprovalDefinitionId definitionId) {
        requireTenant(tenantId);
        if (definitionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(mapper.selectOne(tenantFilter(tenantId)
                .eq(ApprovalDefinitionPO::getId, definitionId.getValue()))));
    }

    @Override
    public List<ApprovalDefinition> findByIds(
            TenantId tenantId, Collection<ApprovalDefinitionId> definitionIds) {
        requireTenant(tenantId);
        if (definitionIds == null || definitionIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = definitionIds.stream().filter(java.util.Objects::nonNull)
                .map(ApprovalDefinitionId::getValue).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(tenantFilter(tenantId)
                        .in(ApprovalDefinitionPO::getId, ids)
                        .orderByAsc(ApprovalDefinitionPO::getId))
                .stream().map(converter::toDomain).toList();
    }

    @Override
    public Optional<ApprovalDefinition> findByIdForUpdate(
            TenantId tenantId, ApprovalDefinitionId definitionId) {
        requireTenant(tenantId);
        if (definitionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(mapper.selectOne(tenantFilter(tenantId)
                .eq(ApprovalDefinitionPO::getId, definitionId.getValue())
                .last("FOR UPDATE"))));
    }

    @Override
    public PageResult<ApprovalDefinition> findPage(
            TenantId tenantId, ApprovalDefinitionQuery query) {
        requireTenant(tenantId);
        if (query == null) {
            throw new IllegalArgumentException("approval definition query is required");
        }
        LambdaQueryWrapper<ApprovalDefinitionPO> filter = tenantFilter(tenantId);
        if (query.objectType() != null) {
            filter.eq(ApprovalDefinitionPO::getObjectType, query.objectType().name());
        }
        if (query.status() != null) {
            filter.eq(ApprovalDefinitionPO::getStatus, query.status().name());
        } else {
            filter.ne(ApprovalDefinitionPO::getStatus, ApprovalDefinitionStatus.DELETED.name());
        }
        if (query.keyword() != null) {
            filter.like(ApprovalDefinitionPO::getName, escapeLike(query.keyword()));
        }
        IPage<ApprovalDefinitionPO> page = mapper.selectPage(
                new Page<>(query.page(), query.size()), filter
                        .orderByDesc(ApprovalDefinitionPO::getUpdatedTime)
                        .orderByDesc(ApprovalDefinitionPO::getId));
        return new PageResult<>(page.getCurrent(), page.getSize(), Math.max(0, page.getTotal()),
                page.getRecords().stream().map(converter::toDomain).toList());
    }

    @Override
    public List<ApprovalDefinition> findActiveByObjectType(
            TenantId tenantId, BusinessObjectType objectType) {
        requireTenant(tenantId);
        if (objectType == null) {
            throw new IllegalArgumentException("approval object type is required");
        }
        return mapper.selectList(tenantFilter(tenantId)
                        .eq(ApprovalDefinitionPO::getObjectType, objectType.name())
                        .eq(ApprovalDefinitionPO::getStatus, ApprovalDefinitionStatus.ACTIVE.name())
                        .orderByAsc(ApprovalDefinitionPO::getName, ApprovalDefinitionPO::getId))
                .stream().map(converter::toDomain).toList();
    }

    @Override
    public boolean existsReferencingField(TenantId tenantId,
                                          BusinessObjectType objectType,
                                          FieldCode fieldCode) {
        requireTenant(tenantId);
        if (objectType == null || fieldCode == null) {
            throw new IllegalArgumentException("approval field reference is required");
        }
        return mapper.countReferencingField(tenantId.getValue(), objectType.name(),
                escapeJsonSearch(fieldCode.getValue())) > 0;
    }

    private LambdaQueryWrapper<ApprovalDefinitionPO> tenantFilter(TenantId tenantId) {
        requireTenant(tenantId);
        return new LambdaQueryWrapper<ApprovalDefinitionPO>()
                .eq(ApprovalDefinitionPO::getTenantId, tenantId.getValue());
    }

    private void requireTenantMatch(TenantId tenantId, ApprovalDefinition definition) {
        requireTenant(tenantId);
        if (definition == null || definition.getTenantId() == null
                || !tenantId.sameValueAs(definition.getTenantId())) {
            throw new IllegalArgumentException("approval definition tenant does not match");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID is required");
        }
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String escapeJsonSearch(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
