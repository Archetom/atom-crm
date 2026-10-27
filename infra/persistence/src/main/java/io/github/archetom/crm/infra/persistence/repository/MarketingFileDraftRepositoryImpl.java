package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.MarketingFileDraft;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.MarketingFileDraftState;
import io.github.archetom.crm.domain.model.FileFieldValue;
import io.github.archetom.crm.domain.repository.MarketingFileDraftRepository;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.MarketingFileDraftId;
import io.github.archetom.crm.domain.valueobject.MarketingFormId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.AttachmentFilePOConverter;
import io.github.archetom.crm.infra.persistence.converter.CustomFieldDefinitionPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.AttachmentFileMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomFieldDefinitionMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.MarketingFileDraftCandidateMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.MarketingFileDraftGuardMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.MarketingFileDraftMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.AttachmentFilePO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomFieldDefinitionPO;
import io.github.archetom.crm.infra.persistence.mysql.po.MarketingFileDraftCandidatePO;
import io.github.archetom.crm.infra.persistence.mysql.po.MarketingFileDraftPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** MySQL store for hash-only anonymous MarketingForm FILE drafts and candidates. */
@Repository
@RequiredArgsConstructor
public class MarketingFileDraftRepositoryImpl implements MarketingFileDraftRepository {
    private final MarketingFileDraftMapper draftMapper;
    private final MarketingFileDraftCandidateMapper candidateMapper;
    private final MarketingFileDraftGuardMapper guardMapper;
    private final CustomFieldDefinitionMapper fieldMapper;
    private final AttachmentFileMapper fileMapper;
    private final CustomFieldDefinitionPOConverter fieldConverter;
    private final AttachmentFilePOConverter fileConverter;

    @Override
    public MarketingFileDraft save(TenantId tenantId, MarketingFileDraft draft) {
        requireTenant(tenantId);
        if (draft == null || draft.getTenantId() == null || !tenantId.sameValueAs(draft.getTenantId())) {
            throw new IllegalArgumentException("marketing file draft tenant does not match");
        }
        MarketingFileDraftPO po = toPO(draft);
        if (draft.getId() == null) {
            if (draftMapper.insert(po) != 1 || po.getId() == null) {
                throw new IllegalStateException("marketing file draft insert failed");
            }
        } else if (draftMapper.update(po, new LambdaUpdateWrapper<MarketingFileDraftPO>()
                .eq(MarketingFileDraftPO::getTenantId, tenantId.getValue())
                .eq(MarketingFileDraftPO::getId, po.getId())) != 1) {
            throw new AggregateVersionConflictException();
        }
        draft.onPersisted(new MarketingFileDraftId(po.getId()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime());
        return draft;
    }

    @Override
    public Optional<MarketingFileDraft> find(TenantId tenantId, MarketingFileDraftId draftId) {
        requireTenant(tenantId);
        if (draftId == null) return Optional.empty();
        return Optional.ofNullable(draftMapper.selectOne(new LambdaQueryWrapper<MarketingFileDraftPO>()
                .eq(MarketingFileDraftPO::getTenantId, tenantId.getValue())
                .eq(MarketingFileDraftPO::getId, draftId.getValue()))).map(this::toDomain);
    }

    @Override
    public Optional<MarketingFileDraft> findForUpdate(TenantId tenantId, MarketingFileDraftId draftId) {
        requireTenant(tenantId);
        if (draftId == null) return Optional.empty();
        return Optional.ofNullable(draftMapper.selectOne(new LambdaQueryWrapper<MarketingFileDraftPO>()
                .eq(MarketingFileDraftPO::getTenantId, tenantId.getValue())
                .eq(MarketingFileDraftPO::getId, draftId.getValue()).last("FOR UPDATE"))).map(this::toDomain);
    }

    @Override
    public Optional<MarketingFileDraft> findForStagedFile(TenantId tenantId, AttachmentFileId fileId) {
        requireTenant(tenantId);
        if (fileId == null) return Optional.empty();
        MarketingFileDraftCandidatePO candidate = candidateMapper.selectOne(
                new LambdaQueryWrapper<MarketingFileDraftCandidatePO>()
                        .eq(MarketingFileDraftCandidatePO::getTenantId, tenantId.getValue())
                        .eq(MarketingFileDraftCandidatePO::getFileId, fileId.getValue())
                        .eq(MarketingFileDraftCandidatePO::getState, "STAGED"));
        return candidate == null ? Optional.empty()
                : find(tenantId, new MarketingFileDraftId(candidate.getDraftId()));
    }

    @Override
    public long countActiveForFormForUpdate(TenantId tenantId, MarketingFormId formId) {
        requireTenant(tenantId);
        if (formId == null) return 0;
        return draftMapper.selectCount(new LambdaQueryWrapper<MarketingFileDraftPO>()
                .eq(MarketingFileDraftPO::getTenantId, tenantId.getValue())
                .eq(MarketingFileDraftPO::getMarketingFormId, formId.getValue())
                .eq(MarketingFileDraftPO::getState, MarketingFileDraftState.ACTIVE.name())
                .gt(MarketingFileDraftPO::getDeadline, LocalDateTime.now(ZoneOffset.UTC)).last("FOR UPDATE"));
    }

    @Override
    public void lockFormCapacity(TenantId tenantId, MarketingFormId formId) {
        requireTenant(tenantId);
        if (formId == null || guardMapper.ensure(tenantId.getValue(), formId.getValue()) != 1
                || guardMapper.lock(tenantId.getValue(), formId.getValue()) == null) {
            throw new IllegalStateException("marketing file draft capacity guard is unavailable");
        }
    }

    @Override
    public void stage(TenantId tenantId, MarketingFileDraftId draftId, FieldDefinition field,
                      AttachmentFileId fileId, int ordinal) {
        requireTenant(tenantId);
        if (draftId == null || field == null || field.getId() == null || fileId == null
                || field.getTenantId() == null || !tenantId.sameValueAs(field.getTenantId())
                || field.getObjectType() != BusinessObjectType.LEAD
                || field.getFieldType() != CustomFieldType.FILE
                || ordinal < 0 || ordinal > 9) {
            throw new IllegalArgumentException("marketing file draft candidate is invalid");
        }
        MarketingFileDraftCandidatePO po = new MarketingFileDraftCandidatePO();
        po.setTenantId(tenantId.getValue());
        po.setDraftId(draftId.getValue());
        po.setObjectType(BusinessObjectType.LEAD.name());
        po.setFieldId(field.getId().getValue());
        po.setFileId(fileId.getValue());
        po.setOrdinal(ordinal);
        po.setState("STAGED");
        if (candidateMapper.insert(po) != 1) {
            throw new IllegalStateException("marketing file draft candidate insert failed");
        }
    }

    @Override
    public List<StagedFile> findStagedFiles(TenantId tenantId, MarketingFileDraftId draftId) {
        return staged(tenantId, draftId, false);
    }

    @Override
    public List<StagedFile> findStagedFilesForUpdate(TenantId tenantId, MarketingFileDraftId draftId) {
        return staged(tenantId, draftId, true);
    }

    @Override
    public void markStagedBound(TenantId tenantId, MarketingFileDraftId draftId,
                                List<FileFieldValue> values) {
        requireTenant(tenantId);
        if (draftId == null || values == null || values.isEmpty()) {
            throw new IllegalArgumentException("marketing file draft binding is invalid");
        }
        for (FileFieldValue value : values) {
            if (value == null || candidateMapper.update(null,
                    new LambdaUpdateWrapper<MarketingFileDraftCandidatePO>()
                            .set(MarketingFileDraftCandidatePO::getState, "BOUND")
                            .set(MarketingFileDraftCandidatePO::getContentVersion, value.contentVersion())
                            .set(MarketingFileDraftCandidatePO::getEvidenceDigest, value.evidenceDigest())
                            .eq(MarketingFileDraftCandidatePO::getTenantId, tenantId.getValue())
                            .eq(MarketingFileDraftCandidatePO::getDraftId, draftId.getValue())
                            .eq(MarketingFileDraftCandidatePO::getFileId, value.fileId().getValue())
                            .eq(MarketingFileDraftCandidatePO::getState, "STAGED")) != 1) {
                throw new IllegalStateException("marketing file draft was already consumed");
            }
        }
    }

    @Override
    public void withdrawStagedFiles(TenantId tenantId, MarketingFileDraftId draftId) {
        requireTenant(tenantId);
        if (draftId == null) throw new IllegalArgumentException("marketing file draft is required");
        candidateMapper.update(null, new LambdaUpdateWrapper<MarketingFileDraftCandidatePO>()
                .set(MarketingFileDraftCandidatePO::getState, "WITHDRAWN")
                .eq(MarketingFileDraftCandidatePO::getTenantId, tenantId.getValue())
                .eq(MarketingFileDraftCandidatePO::getDraftId, draftId.getValue())
                .eq(MarketingFileDraftCandidatePO::getState, "STAGED"));
    }

    private List<StagedFile> staged(TenantId tenantId, MarketingFileDraftId draftId, boolean forUpdate) {
        requireTenant(tenantId);
        if (draftId == null) return List.of();
        List<MarketingFileDraftCandidatePO> rows = candidateMapper.selectList(
                new LambdaQueryWrapper<MarketingFileDraftCandidatePO>()
                        .eq(MarketingFileDraftCandidatePO::getTenantId, tenantId.getValue())
                        .eq(MarketingFileDraftCandidatePO::getDraftId, draftId.getValue())
                        .eq(MarketingFileDraftCandidatePO::getState, "STAGED")
                        .orderByAsc(MarketingFileDraftCandidatePO::getFieldId,
                                MarketingFileDraftCandidatePO::getOrdinal)
                        .last(forUpdate ? "FOR UPDATE" : ""));
        Map<Long, FieldDefinition> fields = fields(tenantId, rows.stream()
                .map(MarketingFileDraftCandidatePO::getFieldId).collect(Collectors.toSet()));
        Map<Long, AttachmentFile> files = files(tenantId, rows.stream()
                .map(MarketingFileDraftCandidatePO::getFileId).collect(Collectors.toSet()), forUpdate);
        return rows.stream().map(value -> new StagedFile(fields.get(value.getFieldId()),
                files.get(value.getFileId()), value.getOrdinal())).peek(value -> {
            if (value.field() == null || value.file() == null) {
                throw new IllegalStateException("stored marketing file draft candidate is incomplete");
            }
        }).toList();
    }

    private Map<Long, FieldDefinition> fields(TenantId tenantId, Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return fieldMapper.selectList(new LambdaQueryWrapper<CustomFieldDefinitionPO>()
                .eq(CustomFieldDefinitionPO::getTenantId, tenantId.getValue())
                .eq(CustomFieldDefinitionPO::getObjectType, BusinessObjectType.LEAD.name())
                .in(CustomFieldDefinitionPO::getId, ids)).stream().map(fieldConverter::toDomain)
                .collect(Collectors.toMap(value -> value.getId().getValue(), Function.identity()));
    }

    private Map<Long, AttachmentFile> files(TenantId tenantId, Set<Long> ids, boolean forUpdate) {
        if (ids.isEmpty()) return Map.of();
        return fileMapper.selectList(new LambdaQueryWrapper<AttachmentFilePO>()
                .eq(AttachmentFilePO::getTenantId, tenantId.getValue())
                .in(AttachmentFilePO::getId, ids).last(forUpdate ? "FOR UPDATE" : ""))
                .stream().map(fileConverter::toDomain).collect(Collectors.toMap(
                        value -> value.getId().getValue(), Function.identity()));
    }

    private MarketingFileDraftPO toPO(MarketingFileDraft value) {
        MarketingFileDraftPO po = new MarketingFileDraftPO();
        po.setId(value.getId() == null ? null : value.getId().getValue());
        po.setTenantId(value.getTenantId().getValue());
        po.setMarketingFormId(value.getFormId().getValue());
        po.setFormVersion(value.getFormVersion());
        po.setCredentialDigest(value.getCredentialDigest());
        po.setDeadline(value.getDeadline());
        po.setState(value.getState().name());
        po.setReservedBytes(value.getReservedBytes());
        po.setActualBytes(value.getActualBytes());
        po.setVersion(value.getVersion());
        po.setCreatedTime(value.getCreatedTime());
        po.setUpdatedTime(value.getUpdatedTime());
        return po;
    }

    private MarketingFileDraft toDomain(MarketingFileDraftPO po) {
        return MarketingFileDraft.reconstitute(new MarketingFileDraft.Snapshot(
                new MarketingFileDraftId(po.getId()), new TenantId(po.getTenantId()),
                new MarketingFormId(po.getMarketingFormId()), po.getFormVersion(),
                po.getCredentialDigest(), po.getDeadline(),
                MarketingFileDraftState.valueOf(po.getState()), po.getReservedBytes(), po.getActualBytes(),
                po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
    }

    private static void requireTenant(TenantId tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("marketing file draft tenant is required");
    }
}
