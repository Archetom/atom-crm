package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.StageWorkflowDefinition;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.StageWorkflowAlreadyExistsException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.StageWorkflowQuery;
import io.github.archetom.crm.domain.model.StageWorkflowStatus;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.StageWorkflowDefinitionRepository;
import io.github.archetom.crm.domain.valueobject.StageWorkflowId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.StageWorkflowDefinitionPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.StageWorkflowDefinitionMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.StageWorkflowDefinitionPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StageWorkflowDefinitionRepositoryImpl implements StageWorkflowDefinitionRepository {

    private final StageWorkflowDefinitionMapper mapper;
    private final StageWorkflowDefinitionPOConverter converter;

    @Override
    public StageWorkflowDefinition save(TenantId tenantId, StageWorkflowDefinition workflow) {
        requireTenantMatch(tenantId, workflow);
        StageWorkflowDefinitionPO po = converter.toPO(workflow);
        try {
            if (po.getId() == null) {
                if (mapper.insert(po) != 1) {
                    throw new IllegalStateException("Stage workflow insert did not affect one row");
                }
            } else if (mapper.update(po, tenantFilter(tenantId)
                    .eq(StageWorkflowDefinitionPO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
        } catch (DuplicateKeyException exception) {
            throw new StageWorkflowAlreadyExistsException();
        }
        workflow.onPersisted(new StageWorkflowId(po.getId()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime());
        return workflow;
    }

    @Override
    public Optional<StageWorkflowDefinition> findById(
            TenantId tenantId, StageWorkflowId workflowId) {
        requireTenant(tenantId);
        if (workflowId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(mapper.selectOne(tenantFilter(tenantId)
                .eq(StageWorkflowDefinitionPO::getId, workflowId.getValue()))));
    }

    @Override
    public PageResult<StageWorkflowDefinition> findPage(
            TenantId tenantId, StageWorkflowQuery query) {
        requireTenant(tenantId);
        if (query == null) {
            throw new IllegalArgumentException("stage workflow query is required");
        }
        LambdaQueryWrapper<StageWorkflowDefinitionPO> filter = tenantFilter(tenantId);
        if (query.objectType() != null) {
            filter.eq(StageWorkflowDefinitionPO::getObjectType, query.objectType().name());
        }
        if (query.status() != null) {
            filter.eq(StageWorkflowDefinitionPO::getStatus, query.status().name());
        } else {
            filter.ne(StageWorkflowDefinitionPO::getStatus, StageWorkflowStatus.DELETED.name());
        }
        if (query.keyword() != null) {
            filter.like(StageWorkflowDefinitionPO::getName, escapeLike(query.keyword()));
        }
        IPage<StageWorkflowDefinitionPO> page = mapper.selectPage(
                new Page<>(query.page(), query.size()), filter
                        .orderByDesc(StageWorkflowDefinitionPO::getUpdatedTime)
                        .orderByDesc(StageWorkflowDefinitionPO::getId));
        return new PageResult<>(page.getCurrent(), page.getSize(), Math.max(0, page.getTotal()),
                page.getRecords().stream().map(converter::toDomain).toList());
    }

    @Override
    public List<StageWorkflowDefinition> findActiveByObjectType(
            TenantId tenantId, BusinessObjectType objectType) {
        requireTenant(tenantId);
        if (objectType == null) {
            throw new IllegalArgumentException("stage workflow object type is required");
        }
        return mapper.selectList(tenantFilter(tenantId)
                        .eq(StageWorkflowDefinitionPO::getObjectType, objectType.name())
                        .eq(StageWorkflowDefinitionPO::getStatus, StageWorkflowStatus.ACTIVE.name())
                        .orderByAsc(StageWorkflowDefinitionPO::getName,
                                StageWorkflowDefinitionPO::getId))
                .stream().map(converter::toDomain).toList();
    }

    @Override
    public boolean existsReferencingField(TenantId tenantId,
                                          BusinessObjectType objectType,
                                          FieldCode fieldCode) {
        requireTenant(tenantId);
        if (objectType == null || fieldCode == null) {
            throw new IllegalArgumentException("stage workflow field reference is required");
        }
        return mapper.countReferencingField(tenantId.getValue(), objectType.name(),
                escapeJsonSearch(fieldCode.getValue())) > 0;
    }

    @Override
    public boolean existsReferencingApprovalDefinition(
            TenantId tenantId, ApprovalDefinitionId approvalDefinitionId) {
        requireTenant(tenantId);
        if (approvalDefinitionId == null) {
            throw new IllegalArgumentException("approval definition reference is required");
        }
        return mapper.countReferencingApprovalDefinition(
                tenantId.getValue(), approvalDefinitionId.getValue()) > 0;
    }

    @Override
    public List<StageWorkflowDefinition> findReferencingApprovalDefinition(
            TenantId tenantId, ApprovalDefinitionId approvalDefinitionId) {
        requireTenant(tenantId);
        if (approvalDefinitionId == null) {
            throw new IllegalArgumentException("approval definition reference is required");
        }
        return mapper.selectReferencingApprovalDefinition(
                        tenantId.getValue(), approvalDefinitionId.getValue())
                .stream().map(converter::toDomain).toList();
    }

    private LambdaQueryWrapper<StageWorkflowDefinitionPO> tenantFilter(TenantId tenantId) {
        requireTenant(tenantId);
        return new LambdaQueryWrapper<StageWorkflowDefinitionPO>()
                .eq(StageWorkflowDefinitionPO::getTenantId, tenantId.getValue());
    }

    private void requireTenantMatch(TenantId tenantId, StageWorkflowDefinition workflow) {
        requireTenant(tenantId);
        if (workflow == null || workflow.getTenantId() == null
                || !tenantId.sameValueAs(workflow.getTenantId())) {
            throw new IllegalArgumentException("stage workflow tenant does not match");
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
