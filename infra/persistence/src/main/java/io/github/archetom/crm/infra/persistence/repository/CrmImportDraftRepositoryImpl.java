package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.CrmImportDraft;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.model.CrmImportDraftState;
import io.github.archetom.crm.domain.model.CrmImportObjectType;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.CrmImportDraftRepository;
import io.github.archetom.crm.domain.valueobject.CrmImportDraftId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CrmImportDraftMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.CrmImportDraftPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** MySQL persistence for tenant-scoped CRM import drafts. */
@Repository
@RequiredArgsConstructor
public class CrmImportDraftRepositoryImpl implements CrmImportDraftRepository {
    private final CrmImportDraftMapper mapper;

    @Override
    public CrmImportDraft save(TenantId tenantId, CrmImportDraft draft) {
        requireTenant(tenantId);
        if (draft == null || draft.getTenantId() == null || !tenantId.sameValueAs(draft.getTenantId())) {
            throw new IllegalArgumentException("CRM import draft tenant does not match");
        }
        CrmImportDraftPO po = toPO(draft);
        if (draft.getId() == null) {
            if (mapper.insert(po) != 1 || po.getId() == null) {
                throw new IllegalStateException("CRM import draft insert failed");
            }
        } else if (mapper.update(po, new LambdaUpdateWrapper<CrmImportDraftPO>()
                .eq(CrmImportDraftPO::getTenantId, tenantId.getValue())
                .eq(CrmImportDraftPO::getId, po.getId())) != 1) {
            throw new AggregateVersionConflictException();
        }
        draft.onPersisted(new CrmImportDraftId(po.getId()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime());
        return draft;
    }

    @Override
    public Optional<CrmImportDraft> find(TenantId tenantId, CrmImportDraftId draftId) {
        requireTenant(tenantId);
        if (draftId == null) return Optional.empty();
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<CrmImportDraftPO>()
                .eq(CrmImportDraftPO::getTenantId, tenantId.getValue())
                .eq(CrmImportDraftPO::getId, draftId.getValue()))).map(CrmImportDraftRepositoryImpl::toDomain);
    }

    @Override
    public Optional<CrmImportDraft> findForUpdate(TenantId tenantId, CrmImportDraftId draftId) {
        requireTenant(tenantId);
        if (draftId == null) return Optional.empty();
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<CrmImportDraftPO>()
                .eq(CrmImportDraftPO::getTenantId, tenantId.getValue())
                .eq(CrmImportDraftPO::getId, draftId.getValue()).last("FOR UPDATE")))
                .map(CrmImportDraftRepositoryImpl::toDomain);
    }

    @Override
    public List<CrmImportDraft> findDueActive(LocalDateTime deadline, int limit) {
        if (deadline == null || limit < 1 || limit > 25) {
            throw new IllegalArgumentException("CRM import expiry criteria are invalid");
        }
        return mapper.selectList(new LambdaQueryWrapper<CrmImportDraftPO>()
                        .eq(CrmImportDraftPO::getState, CrmImportDraftState.ACTIVE.name())
                        .le(CrmImportDraftPO::getDeadline, deadline)
                        .orderByAsc(CrmImportDraftPO::getDeadline)
                        .orderByAsc(CrmImportDraftPO::getId)
                        .last("LIMIT " + limit))
                .stream().map(CrmImportDraftRepositoryImpl::toDomain).toList();
    }

    @Override
    public PageResult<CrmImportDraft> findCompletedByUploader(
            TenantId tenantId, MemberId uploaderMemberId, CrmImportObjectType objectType,
            LocalDateTime completedSince, long page, long size) {
        requireTenant(tenantId);
        if (uploaderMemberId == null || objectType == null || completedSince == null
                || page < 1 || size < 1) {
            throw new IllegalArgumentException("CRM import history criteria are invalid");
        }
        Page<CrmImportDraftPO> result = mapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<CrmImportDraftPO>()
                        .eq(CrmImportDraftPO::getTenantId, tenantId.getValue())
                        .eq(CrmImportDraftPO::getUploaderActorId, uploaderMemberId.getValue())
                        .eq(CrmImportDraftPO::getObjectType, objectType.name())
                        .eq(CrmImportDraftPO::getState, CrmImportDraftState.CONSUMED.name())
                        .ge(CrmImportDraftPO::getUpdatedTime, completedSince)
                        .orderByDesc(CrmImportDraftPO::getUpdatedTime)
                        .orderByDesc(CrmImportDraftPO::getId));
        return new PageResult<>(result.getCurrent(), result.getSize(), Math.max(0L, result.getTotal()),
                result.getRecords().stream().map(CrmImportDraftRepositoryImpl::toDomain).toList());
    }

    private static CrmImportDraftPO toPO(CrmImportDraft value) {
        CrmImportDraftPO po = new CrmImportDraftPO();
        po.setId(value.getId() == null ? null : value.getId().getValue());
        po.setTenantId(value.getTenantId().getValue());
        po.setUploaderActorId(value.getUploaderMemberId().getValue());
        po.setObjectType(value.getObjectType().name());
        po.setDeadline(value.getDeadline());
        po.setState(value.getState().name());
        po.setImportedRows(value.getImportedRows());
        po.setVersion(value.getVersion());
        po.setCreatedTime(value.getCreatedTime());
        po.setUpdatedTime(value.getUpdatedTime());
        return po;
    }

    private static CrmImportDraft toDomain(CrmImportDraftPO po) {
        return CrmImportDraft.reconstitute(new CrmImportDraft.Snapshot(
                new CrmImportDraftId(po.getId()), new TenantId(po.getTenantId()),
                new MemberId(po.getUploaderActorId()), CrmImportObjectType.valueOf(po.getObjectType()),
                po.getDeadline(), CrmImportDraftState.valueOf(po.getState()), po.getImportedRows(),
                po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
    }

    private static void requireTenant(TenantId tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("CRM import draft tenant is required");
    }
}
