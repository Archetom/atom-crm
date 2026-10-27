package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.archetom.crm.domain.entity.StageWorkflowRecord;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.StageWorkflowAlreadyExistsException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.StageWorkflowRecordStatus;
import io.github.archetom.crm.domain.repository.StageWorkflowRecordRepository;
import io.github.archetom.crm.domain.valueobject.StageWorkflowId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowRecordId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.StageWorkflowRecordPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.StageWorkflowRecordMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.StageWorkflowRecordPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StageWorkflowRecordRepositoryImpl implements StageWorkflowRecordRepository {

    private final StageWorkflowRecordMapper mapper;
    private final StageWorkflowRecordPOConverter converter;

    @Override
    public StageWorkflowRecord save(TenantId tenantId, StageWorkflowRecord record) {
        requireTenantMatch(tenantId, record);
        StageWorkflowRecordPO po = converter.toPO(record);
        try {
            if (po.getId() == null) {
                if (mapper.insert(po) != 1) {
                    throw new IllegalStateException("Stage workflow record insert did not affect one row");
                }
            } else if (mapper.update(po, tenantFilter(tenantId)
                    .eq(StageWorkflowRecordPO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
        } catch (DuplicateKeyException exception) {
            throw new StageWorkflowAlreadyExistsException();
        }
        record.onPersisted(new StageWorkflowRecordId(po.getId()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime());
        return record;
    }

    @Override
    public Optional<StageWorkflowRecord> findById(
            TenantId tenantId, StageWorkflowRecordId recordId) {
        requireTenant(tenantId);
        if (recordId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(mapper.selectOne(tenantFilter(tenantId)
                .eq(StageWorkflowRecordPO::getId, recordId.getValue()))));
    }

    @Override
    public Optional<StageWorkflowRecord> findByIdForUpdate(
            TenantId tenantId, StageWorkflowRecordId recordId) {
        requireTenant(tenantId);
        if (recordId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(mapper.selectOne(tenantFilter(tenantId)
                .eq(StageWorkflowRecordPO::getId, recordId.getValue()).last("FOR UPDATE"))));
    }

    @Override
    public Optional<StageWorkflowRecord> findBySubject(
            TenantId tenantId, BusinessObjectType objectType, Long subjectId) {
        requireTenant(tenantId);
        if (objectType == null || subjectId == null || subjectId <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(mapper.selectOne(tenantFilter(tenantId)
                .eq(StageWorkflowRecordPO::getObjectType, objectType.name())
                .eq(StageWorkflowRecordPO::getSubjectId, subjectId))));
    }

    @Override
    public boolean existsByWorkflow(TenantId tenantId, StageWorkflowId workflowId) {
        requireTenant(tenantId);
        if (workflowId == null) {
            return false;
        }
        return mapper.selectCount(tenantFilter(tenantId)
                .eq(StageWorkflowRecordPO::getWorkflowId, workflowId.getValue())) > 0;
    }

    @Override
    public boolean existsNonTerminalByWorkflow(
            TenantId tenantId, StageWorkflowId workflowId) {
        requireTenant(tenantId);
        if (workflowId == null) {
            return false;
        }
        return mapper.selectCount(tenantFilter(tenantId)
                .eq(StageWorkflowRecordPO::getWorkflowId, workflowId.getValue())
                .in(StageWorkflowRecordPO::getStatus,
                        StageWorkflowRecordStatus.ACTIVE.name(),
                        StageWorkflowRecordStatus.AWAITING_APPROVAL.name())) > 0;
    }

    private LambdaQueryWrapper<StageWorkflowRecordPO> tenantFilter(TenantId tenantId) {
        requireTenant(tenantId);
        return new LambdaQueryWrapper<StageWorkflowRecordPO>()
                .eq(StageWorkflowRecordPO::getTenantId, tenantId.getValue());
    }

    private void requireTenantMatch(TenantId tenantId, StageWorkflowRecord record) {
        requireTenant(tenantId);
        if (record == null || record.getTenantId() == null
                || !tenantId.sameValueAs(record.getTenantId())) {
            throw new IllegalArgumentException("stage workflow record tenant does not match");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID is required");
        }
    }
}
