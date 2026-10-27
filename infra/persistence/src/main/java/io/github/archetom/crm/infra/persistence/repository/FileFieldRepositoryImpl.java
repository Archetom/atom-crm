package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FileFieldDraft;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.FileFieldDraftState;
import io.github.archetom.crm.domain.model.FileFieldValue;
import io.github.archetom.crm.domain.repository.FileFieldRepository;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FileFieldDraftId;
import io.github.archetom.crm.domain.valueobject.OaFormId;
import io.github.archetom.crm.domain.valueobject.OaRequestId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowRecordId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.AttachmentFilePOConverter;
import io.github.archetom.crm.infra.persistence.converter.CustomFieldDefinitionPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.AttachmentFieldBindingMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ApprovalInstanceFileFactMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ApprovalInstanceFileSnapshotMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.AttachmentFileMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomFieldDefinitionMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.FileFieldDraftFileMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.FileFieldDraftMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OaRequestFileValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.StageWorkflowFileFactMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.StageWorkflowFileSnapshotMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.AttachmentFieldBindingPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ApprovalInstanceFileFactPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ApprovalInstanceFileSnapshotPO;
import io.github.archetom.crm.infra.persistence.mysql.po.AttachmentFilePO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomFieldDefinitionPO;
import io.github.archetom.crm.infra.persistence.mysql.po.FileFieldDraftFilePO;
import io.github.archetom.crm.infra.persistence.mysql.po.FileFieldDraftPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OaRequestFileValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.StageWorkflowFileFactPO;
import io.github.archetom.crm.infra.persistence.mysql.po.StageWorkflowFileSnapshotPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** MySQL implementation for B24c's non-scalar file-field relations. */
@Repository
@RequiredArgsConstructor
public class FileFieldRepositoryImpl implements FileFieldRepository {
    private final FileFieldDraftMapper draftMapper;
    private final FileFieldDraftFileMapper draftFileMapper;
    private final AttachmentFieldBindingMapper bindingMapper;
    private final OaRequestFileValueMapper oaValueMapper;
    private final ApprovalInstanceFileFactMapper approvalFactMapper;
    private final ApprovalInstanceFileSnapshotMapper approvalSnapshotMapper;
    private final StageWorkflowFileFactMapper stageFactMapper;
    private final StageWorkflowFileSnapshotMapper stageSnapshotMapper;
    private final AttachmentFileMapper fileMapper;
    private final CustomFieldDefinitionMapper fieldMapper;
    private final AttachmentFilePOConverter fileConverter;
    private final CustomFieldDefinitionPOConverter fieldConverter;

    @Override
    public FileFieldDraft saveDraft(TenantId tenantId, FileFieldDraft draft) {
        requireTenant(tenantId);
        if (draft == null || draft.getTenantId() == null || !tenantId.sameValueAs(draft.getTenantId())) {
            throw new IllegalArgumentException("file field draft tenant does not match");
        }
        FileFieldDraftPO po = toPO(draft);
        if (draft.getId() == null) {
            if (draftMapper.insert(po) != 1 || po.getId() == null) {
                throw new IllegalStateException("file field draft insert failed");
            }
        } else if (draftMapper.updateById(po) != 1) {
            throw new IllegalStateException("file field draft update failed");
        }
        draft.onPersisted(new FileFieldDraftId(po.getId()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime());
        return draft;
    }

    @Override
    public Optional<FileFieldDraft> findDraftForUpdate(TenantId tenantId, FileFieldDraftId draftId) {
        requireTenant(tenantId);
        if (draftId == null) return Optional.empty();
        return Optional.ofNullable(draftMapper.selectOne(new LambdaQueryWrapper<FileFieldDraftPO>()
                .eq(FileFieldDraftPO::getTenantId, tenantId.getValue())
                .eq(FileFieldDraftPO::getId, draftId.getValue()).last("FOR UPDATE"))).map(this::toDomain);
    }

    @Override
    public Optional<FileFieldDraft> findDraft(TenantId tenantId, FileFieldDraftId draftId) {
        requireTenant(tenantId);
        if (draftId == null) return Optional.empty();
        return Optional.ofNullable(draftMapper.selectOne(new LambdaQueryWrapper<FileFieldDraftPO>()
                .eq(FileFieldDraftPO::getTenantId, tenantId.getValue())
                .eq(FileFieldDraftPO::getId, draftId.getValue()))).map(this::toDomain);
    }

    @Override
    public Optional<FileFieldDraft> findDraftForStagedFile(
            TenantId tenantId, AttachmentFileId fileId) {
        requireTenant(tenantId);
        if (fileId == null) return Optional.empty();
        FileFieldDraftFilePO staged = draftFileMapper.selectOne(new LambdaQueryWrapper<FileFieldDraftFilePO>()
                .eq(FileFieldDraftFilePO::getTenantId, tenantId.getValue())
                .eq(FileFieldDraftFilePO::getFileId, fileId.getValue())
                .eq(FileFieldDraftFilePO::getState, "STAGED"));
        return staged == null ? Optional.empty()
                : findDraft(tenantId, new FileFieldDraftId(staged.getDraftId()));
    }

    @Override
    public void stage(TenantId tenantId, FileFieldDraftId draftId, FieldDefinition field,
                      AttachmentFileId fileId, int ordinal) {
        requireTenant(tenantId);
        if (draftId == null || field == null || field.getId() == null || fileId == null
                || field.getTenantId() == null || !tenantId.sameValueAs(field.getTenantId())
                || field.getFieldType() != io.github.archetom.crm.domain.model.CustomFieldType.FILE
                || ordinal < 0 || ordinal > 9) {
            throw new IllegalArgumentException("file field staging input is invalid");
        }
        FileFieldDraftFilePO po = new FileFieldDraftFilePO();
        po.setTenantId(tenantId.getValue());
        po.setDraftId(draftId.getValue());
        po.setObjectType(field.getObjectType().name());
        po.setFieldId(field.getId().getValue());
        po.setFileId(fileId.getValue());
        po.setOrdinal(ordinal);
        po.setState("STAGED");
        if (draftFileMapper.insert(po) != 1) {
            throw new IllegalStateException("file field staging insert failed");
        }
    }

    @Override
    public List<StagedFile> findStagedFilesForUpdate(TenantId tenantId, FileFieldDraftId draftId) {
        return staged(tenantId, draftId, true);
    }

    @Override
    public List<StagedFile> findStagedFiles(TenantId tenantId, FileFieldDraftId draftId) {
        return staged(tenantId, draftId, false);
    }

    @Override
    public void bindCrm(TenantId tenantId, FileFieldDraftId draftId, AttachmentTarget target,
                        List<FileFieldValue> values) {
        requireTenant(tenantId); requireCrmTarget(target);
        List<FileFieldValue> normalized = requireValues(values);
        replaceCrm(tenantId, target, grouped(normalized));
        markBound(tenantId, draftId, normalized);
    }

    @Override
    public void replaceCrm(TenantId tenantId, AttachmentTarget target,
                           Map<FieldCode, List<FileFieldValue>> values) {
        requireTenant(tenantId); requireCrmTarget(target);
        if (values == null) throw new IllegalArgumentException("file field values are required");
        BusinessObjectType objectType = BusinessObjectType.valueOf(target.type().name());
        LinkedHashMap<Long, List<FileFieldValue>> byField = new LinkedHashMap<>();
        for (Map.Entry<FieldCode, List<FileFieldValue>> entry : values.entrySet()) {
            if (entry.getKey() == null) throw new IllegalArgumentException("file field code is required");
            List<FileFieldValue> normalized = requireValues(entry.getValue());
            if (normalized.stream().anyMatch(value -> !entry.getKey().equals(value.fieldCode()))) {
                throw new IllegalArgumentException("file field value does not match its field");
            }
            byField.put(fieldId(tenantId, objectType, entry.getKey()), normalized);
        }
        for (Map.Entry<Long, List<FileFieldValue>> entry : byField.entrySet()) {
            int withdrawn = bindingMapper.update(null, new UpdateWrapper<AttachmentFieldBindingPO>()
                    .set("state", "WITHDRAWN")
                    .eq("tenant_id", tenantId.getValue())
                    .eq("field_id", entry.getKey())
                    .eq(targetColumn(target), target.id())
                    .eq("state", "ACTIVE"));
            // A no-op is normal for the first value.  The update remains target/field bounded.
            if (withdrawn < 0) throw new IllegalStateException("file field binding update failed");
            for (FileFieldValue value : entry.getValue()) {
                AttachmentFieldBindingPO po = new AttachmentFieldBindingPO();
                po.setTenantId(tenantId.getValue());
                po.setFileId(value.fileId().getValue());
                po.setObjectType(target.type().name());
                po.setFieldId(entry.getKey());
                setTarget(po, target);
                po.setOrdinal(value.ordinal());
                po.setContentVersion(value.contentVersion());
                po.setEvidenceDigest(value.evidenceDigest());
                po.setState("ACTIVE");
                if (bindingMapper.insert(po) != 1) {
                    throw new IllegalStateException("file field binding insert failed");
                }
            }
        }
    }

    @Override
    public void withdrawCrmField(TenantId tenantId, AttachmentTarget target, FieldCode fieldCode) {
        requireTenant(tenantId); requireCrmTarget(target);
        long fieldId = fieldId(tenantId, BusinessObjectType.valueOf(target.type().name()), fieldCode);
        bindingMapper.update(null, new UpdateWrapper<AttachmentFieldBindingPO>()
                .set("state", "WITHDRAWN")
                .eq("tenant_id", tenantId.getValue())
                .eq("field_id", fieldId)
                .eq(targetColumn(target), target.id())
                .eq("state", "ACTIVE"));
    }

    @Override
    public void bindOa(TenantId tenantId, FileFieldDraftId draftId, OaRequestId requestId,
                       List<FileFieldValue> values) {
        requireTenant(tenantId);
        if (draftId == null || requestId == null) throw new IllegalArgumentException("OA file binding is invalid");
        List<FileFieldValue> normalized = requireValues(values);
        for (FileFieldValue value : normalized) {
            OaRequestFileValuePO po = new OaRequestFileValuePO();
            po.setTenantId(tenantId.getValue());
            po.setOaRequestId(requestId.getValue());
            po.setObjectType(BusinessObjectType.OA_REQUEST.name());
            po.setFieldId(fieldId(tenantId, BusinessObjectType.OA_REQUEST, value.fieldCode()));
            po.setFileId(value.fileId().getValue());
            po.setOrdinal(value.ordinal());
            po.setContentVersion(value.contentVersion());
            po.setEvidenceDigest(value.evidenceDigest());
            if (oaValueMapper.insert(po) != 1) throw new IllegalStateException("OA file value insert failed");
        }
        markBound(tenantId, draftId, normalized);
    }

    @Override
    public void snapshotApprovalInstance(TenantId tenantId, ApprovalInstanceId instanceId,
                                         BusinessObjectType objectType,
                                         Map<FieldCode, List<FileFieldValue>> values) {
        requireTenant(tenantId);
        if (instanceId == null || objectType == null || values == null) {
            throw new IllegalArgumentException("approval file facts are required");
        }
        if (!captureApprovalSnapshot(tenantId, instanceId)) return;
        for (Map.Entry<FieldCode, List<FileFieldValue>> entry : values.entrySet()) {
            long fieldId = fieldId(tenantId, objectType, entry.getKey());
            for (FileFieldValue value : requireValues(entry.getValue())) {
                if (!entry.getKey().equals(value.fieldCode())) {
                    throw new IllegalArgumentException("approval file fact field does not match");
                }
                ApprovalInstanceFileFactPO po = new ApprovalInstanceFileFactPO();
                po.setTenantId(tenantId.getValue());
                po.setInstanceId(instanceId.getValue());
                po.setObjectType(objectType.name());
                po.setFieldId(fieldId);
                po.setFileId(value.fileId().getValue());
                po.setOrdinal(value.ordinal());
                po.setContentVersion(value.contentVersion());
                po.setEvidenceDigest(copy(value.evidenceDigest()));
                if (approvalFactMapper.insert(po) != 1) {
                    throw new IllegalStateException("approval file fact insert failed");
                }
            }
        }
    }

    @Override
    public void snapshotStageWorkflowRecord(TenantId tenantId, StageWorkflowRecordId recordId,
                                            BusinessObjectType objectType,
                                            String stageCode,
                                            Map<FieldCode, List<FileFieldValue>> values) {
        requireTenant(tenantId);
        if (recordId == null || objectType == null || stageCode == null || stageCode.isBlank() || values == null) {
            throw new IllegalArgumentException("stage file facts are required");
        }
        if (!captureStageSnapshot(tenantId, recordId, stageCode)) return;
        for (Map.Entry<FieldCode, List<FileFieldValue>> entry : values.entrySet()) {
            long fieldId = fieldId(tenantId, objectType, entry.getKey());
            for (FileFieldValue value : requireValues(entry.getValue())) {
                if (!entry.getKey().equals(value.fieldCode())) {
                    throw new IllegalArgumentException("stage file fact field does not match");
                }
                StageWorkflowFileFactPO po = new StageWorkflowFileFactPO();
                po.setTenantId(tenantId.getValue());
                po.setRecordId(recordId.getValue());
                po.setStageCode(stageCode);
                po.setObjectType(objectType.name());
                po.setFieldId(fieldId);
                po.setFileId(value.fileId().getValue());
                po.setOrdinal(value.ordinal());
                po.setContentVersion(value.contentVersion());
                po.setEvidenceDigest(copy(value.evidenceDigest()));
                if (stageFactMapper.insert(po) != 1) {
                    throw new IllegalStateException("stage file fact insert failed");
                }
            }
        }
    }

    @Override
    public void markStagedBound(TenantId tenantId, FileFieldDraftId draftId,
                                List<FileFieldValue> values) {
        requireTenant(tenantId);
        markBound(tenantId, draftId, requireValues(values));
    }

    @Override
    public void withdrawStagedFiles(TenantId tenantId, FileFieldDraftId draftId) {
        requireTenant(tenantId);
        if (draftId == null) throw new IllegalArgumentException("file field draft is required");
        draftFileMapper.update(null, new LambdaUpdateWrapper<FileFieldDraftFilePO>()
                .set(FileFieldDraftFilePO::getState, "WITHDRAWN")
                .eq(FileFieldDraftFilePO::getTenantId, tenantId.getValue())
                .eq(FileFieldDraftFilePO::getDraftId, draftId.getValue())
                .eq(FileFieldDraftFilePO::getState, "STAGED"));
    }

    @Override
    public Map<FieldCode, List<BoundFile>> findCrmValues(TenantId tenantId, AttachmentTarget target) {
        requireTenant(tenantId); requireCrmTarget(target);
        List<AttachmentFieldBindingPO> rows = bindingMapper.selectList(
                new QueryWrapper<AttachmentFieldBindingPO>()
                        .eq("tenant_id", tenantId.getValue())
                        .eq("object_type", target.type().name())
                        .eq(targetColumn(target), target.id())
                        .eq("state", "ACTIVE")
                        .orderByAsc("field_id", "ordinal"));
        return bound(tenantId, rows, AttachmentFieldBindingPO::getFieldId,
                AttachmentFieldBindingPO::getFileId, AttachmentFieldBindingPO::getOrdinal,
                AttachmentFieldBindingPO::getContentVersion, AttachmentFieldBindingPO::getEvidenceDigest);
    }

    @Override
    public Map<Long, Map<FieldCode, List<BoundFile>>> findCrmValues(
            TenantId tenantId, AttachmentTargetType targetType, Collection<Long> targetIds) {
        requireTenant(tenantId);
        requireCrmTargetType(targetType);
        if (targetIds == null || targetIds.isEmpty()) return Map.of();
        Set<Long> ids = targetIds.stream().filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (ids.isEmpty()) return Map.of();

        String column = targetColumn(targetType);
        List<AttachmentFieldBindingPO> rows = bindingMapper.selectList(
                new QueryWrapper<AttachmentFieldBindingPO>()
                        .eq("tenant_id", tenantId.getValue())
                        .eq("object_type", targetType.name())
                        .in(column, ids)
                        .eq("state", "ACTIVE")
                        .orderByAsc(column, "field_id", "ordinal"));
        if (rows.isEmpty()) return Map.of();

        // Field definitions and files resolve once for the whole page rather than per target.
        Map<Long, FieldDefinition> fields = fields(tenantId,
                rows.stream().map(AttachmentFieldBindingPO::getFieldId).collect(Collectors.toSet()));
        Map<Long, AttachmentFile> files = files(tenantId,
                rows.stream().map(AttachmentFieldBindingPO::getFileId).collect(Collectors.toSet()), false);

        LinkedHashMap<Long, Map<FieldCode, List<BoundFile>>> result = new LinkedHashMap<>();
        for (AttachmentFieldBindingPO row : rows) {
            Long targetId = targetId(row, targetType);
            FieldDefinition field = fields.get(row.getFieldId());
            AttachmentFile file = files.get(row.getFileId());
            if (targetId == null || field == null || file == null) {
                throw new IllegalStateException("stored file field binding is incomplete");
            }
            result.computeIfAbsent(targetId, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(field.getCode(), ignored -> new ArrayList<>())
                    .add(new BoundFile(field, file, row.getOrdinal(), row.getContentVersion(),
                            copy(row.getEvidenceDigest())));
        }
        return result;
    }

    @Override
    public Map<FieldCode, List<BoundFile>> findOaValues(TenantId tenantId, OaRequestId requestId) {
        requireTenant(tenantId);
        if (requestId == null) return Map.of();
        List<OaRequestFileValuePO> rows = oaValueMapper.selectList(
                new LambdaQueryWrapper<OaRequestFileValuePO>()
                        .eq(OaRequestFileValuePO::getTenantId, tenantId.getValue())
                        .eq(OaRequestFileValuePO::getOaRequestId, requestId.getValue())
                        .orderByAsc(OaRequestFileValuePO::getFieldId,
                                OaRequestFileValuePO::getOrdinal));
        return bound(tenantId, rows, OaRequestFileValuePO::getFieldId,
                OaRequestFileValuePO::getFileId, OaRequestFileValuePO::getOrdinal,
                OaRequestFileValuePO::getContentVersion, OaRequestFileValuePO::getEvidenceDigest);
    }

    @Override
    public Map<Long, Map<FieldCode, List<BoundFile>>> findOaValues(
            TenantId tenantId, Collection<OaRequestId> requestIds) {
        requireTenant(tenantId);
        if (requestIds == null || requestIds.isEmpty()) return Map.of();
        Set<Long> ids = requestIds.stream().filter(java.util.Objects::nonNull)
                .map(OaRequestId::getValue).filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (ids.isEmpty()) return Map.of();

        List<OaRequestFileValuePO> rows = oaValueMapper.selectList(
                new LambdaQueryWrapper<OaRequestFileValuePO>()
                        .eq(OaRequestFileValuePO::getTenantId, tenantId.getValue())
                        .in(OaRequestFileValuePO::getOaRequestId, ids)
                        .orderByAsc(OaRequestFileValuePO::getOaRequestId,
                                OaRequestFileValuePO::getFieldId, OaRequestFileValuePO::getOrdinal));
        if (rows.isEmpty()) return Map.of();

        Map<Long, FieldDefinition> fields = fields(tenantId,
                rows.stream().map(OaRequestFileValuePO::getFieldId).collect(Collectors.toSet()));
        Map<Long, AttachmentFile> files = files(tenantId,
                rows.stream().map(OaRequestFileValuePO::getFileId).collect(Collectors.toSet()), false);

        LinkedHashMap<Long, Map<FieldCode, List<BoundFile>>> result = new LinkedHashMap<>();
        for (OaRequestFileValuePO row : rows) {
            FieldDefinition field = fields.get(row.getFieldId());
            AttachmentFile file = files.get(row.getFileId());
            if (field == null || file == null) {
                throw new IllegalStateException("stored file field binding is incomplete");
            }
            result.computeIfAbsent(row.getOaRequestId(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(field.getCode(), ignored -> new ArrayList<>())
                    .add(new BoundFile(field, file, row.getOrdinal(), row.getContentVersion(),
                            copy(row.getEvidenceDigest())));
        }
        return result;
    }

    @Override
    public Map<FieldCode, List<BoundFile>> findApprovalInstanceValues(TenantId tenantId,
                                                                        ApprovalInstanceId instanceId) {
        requireTenant(tenantId);
        if (instanceId == null) return Map.of();
        List<ApprovalInstanceFileFactPO> rows = approvalFactMapper.selectList(
                new LambdaQueryWrapper<ApprovalInstanceFileFactPO>()
                        .eq(ApprovalInstanceFileFactPO::getTenantId, tenantId.getValue())
                        .eq(ApprovalInstanceFileFactPO::getInstanceId, instanceId.getValue())
                        .orderByAsc(ApprovalInstanceFileFactPO::getFieldId,
                                ApprovalInstanceFileFactPO::getOrdinal));
        return bound(tenantId, rows, ApprovalInstanceFileFactPO::getFieldId,
                ApprovalInstanceFileFactPO::getFileId, ApprovalInstanceFileFactPO::getOrdinal,
                ApprovalInstanceFileFactPO::getContentVersion,
                ApprovalInstanceFileFactPO::getEvidenceDigest);
    }

    @Override
    public Map<String, Map<FieldCode, List<BoundFile>>> findStageWorkflowValues(TenantId tenantId,
                                                                                   StageWorkflowRecordId recordId) {
        requireTenant(tenantId);
        if (recordId == null) return Map.of();
        LinkedHashMap<String, List<StageWorkflowFileFactPO>> byStage = new LinkedHashMap<>();
        stageSnapshotMapper.selectList(new LambdaQueryWrapper<StageWorkflowFileSnapshotPO>()
                        .eq(StageWorkflowFileSnapshotPO::getTenantId, tenantId.getValue())
                        .eq(StageWorkflowFileSnapshotPO::getRecordId, recordId.getValue())
                        .orderByAsc(StageWorkflowFileSnapshotPO::getStageCode))
                .forEach(header -> byStage.put(header.getStageCode(), new ArrayList<>()));
        List<StageWorkflowFileFactPO> rows = stageFactMapper.selectList(
                new LambdaQueryWrapper<StageWorkflowFileFactPO>()
                        .eq(StageWorkflowFileFactPO::getTenantId, tenantId.getValue())
                        .eq(StageWorkflowFileFactPO::getRecordId, recordId.getValue())
                        .orderByAsc(StageWorkflowFileFactPO::getStageCode,
                                StageWorkflowFileFactPO::getFieldId,
                                StageWorkflowFileFactPO::getOrdinal));
        rows.forEach(row -> byStage.computeIfAbsent(row.getStageCode(), ignored -> new ArrayList<>()).add(row));
        return byStage.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> bound(tenantId, entry.getValue(), StageWorkflowFileFactPO::getFieldId,
                        StageWorkflowFileFactPO::getFileId, StageWorkflowFileFactPO::getOrdinal,
                        StageWorkflowFileFactPO::getContentVersion,
                        StageWorkflowFileFactPO::getEvidenceDigest),
                (left, right) -> left, LinkedHashMap::new));
    }

    @Override
    public boolean hasCurrentDependency(TenantId tenantId, FieldDefinition field) {
        requireTenant(tenantId);
        if (field == null || field.getId() == null) return false;
        long fieldId = field.getId().getValue();
        List<Long> activeDraftIds = draftFileMapper.selectList(new LambdaQueryWrapper<FileFieldDraftFilePO>()
                        .eq(FileFieldDraftFilePO::getTenantId, tenantId.getValue())
                        .eq(FileFieldDraftFilePO::getFieldId, fieldId)
                        .eq(FileFieldDraftFilePO::getState, "STAGED")).stream()
                .map(FileFieldDraftFilePO::getDraftId).distinct().toList();
        boolean stagedByActiveDraft = !activeDraftIds.isEmpty()
                && draftMapper.selectCount(new LambdaQueryWrapper<FileFieldDraftPO>()
                        .eq(FileFieldDraftPO::getTenantId, tenantId.getValue())
                        .in(FileFieldDraftPO::getId, activeDraftIds)
                        .eq(FileFieldDraftPO::getState, FileFieldDraftState.ACTIVE.name())
                        .gt(FileFieldDraftPO::getDeadline, LocalDateTime.now(java.time.ZoneOffset.UTC))) > 0;
        return stagedByActiveDraft
                || bindingMapper.selectCount(new LambdaQueryWrapper<AttachmentFieldBindingPO>()
                        .eq(AttachmentFieldBindingPO::getTenantId, tenantId.getValue())
                        .eq(AttachmentFieldBindingPO::getFieldId, fieldId)
                        .eq(AttachmentFieldBindingPO::getState, "ACTIVE")) > 0
                || oaValueMapper.selectCount(new LambdaQueryWrapper<OaRequestFileValuePO>()
                        .eq(OaRequestFileValuePO::getTenantId, tenantId.getValue())
                        .eq(OaRequestFileValuePO::getFieldId, fieldId)) > 0
                || approvalFactMapper.selectCount(new LambdaQueryWrapper<ApprovalInstanceFileFactPO>()
                        .eq(ApprovalInstanceFileFactPO::getTenantId, tenantId.getValue())
                        .eq(ApprovalInstanceFileFactPO::getFieldId, fieldId)) > 0
                || stageFactMapper.selectCount(new LambdaQueryWrapper<StageWorkflowFileFactPO>()
                        .eq(StageWorkflowFileFactPO::getTenantId, tenantId.getValue())
                        .eq(StageWorkflowFileFactPO::getFieldId, fieldId)) > 0;
    }

    private List<StagedFile> staged(TenantId tenantId, FileFieldDraftId draftId, boolean forUpdate) {
        requireTenant(tenantId);
        if (draftId == null) return List.of();
        List<FileFieldDraftFilePO> rows = draftFileMapper.selectList(new LambdaQueryWrapper<FileFieldDraftFilePO>()
                .eq(FileFieldDraftFilePO::getTenantId, tenantId.getValue())
                .eq(FileFieldDraftFilePO::getDraftId, draftId.getValue())
                .eq(FileFieldDraftFilePO::getState, "STAGED")
                .orderByAsc(FileFieldDraftFilePO::getFieldId, FileFieldDraftFilePO::getOrdinal)
                .last(forUpdate ? "FOR UPDATE" : ""));
        Map<Long, FieldDefinition> fields = fields(tenantId, rows.stream()
                .map(FileFieldDraftFilePO::getFieldId).collect(Collectors.toSet()));
        Map<Long, AttachmentFile> files = files(tenantId, rows.stream()
                .map(FileFieldDraftFilePO::getFileId).collect(Collectors.toSet()), forUpdate);
        return rows.stream().map(row -> new StagedFile(fields.get(row.getFieldId()),
                files.get(row.getFileId()), row.getOrdinal())).peek(value -> {
            if (value.field() == null || value.file() == null) {
                throw new IllegalStateException("stored file field staging is incomplete");
            }
        }).toList();
    }

    private <T> Map<FieldCode, List<BoundFile>> bound(TenantId tenantId, Collection<T> rows,
            Function<T, Long> fieldId, Function<T, Long> fileId, Function<T, Integer> ordinal,
            Function<T, Long> contentVersion, Function<T, byte[]> evidenceDigest) {
        if (rows.isEmpty()) return Map.of();
        Map<Long, FieldDefinition> fields = fields(tenantId, rows.stream().map(fieldId).collect(Collectors.toSet()));
        Map<Long, AttachmentFile> files = files(tenantId, rows.stream().map(fileId).collect(Collectors.toSet()), false);
        LinkedHashMap<FieldCode, List<BoundFile>> result = new LinkedHashMap<>();
        for (T row : rows) {
            FieldDefinition field = fields.get(fieldId.apply(row));
            AttachmentFile file = files.get(fileId.apply(row));
            if (field == null || file == null) throw new IllegalStateException("stored file field binding is incomplete");
            result.computeIfAbsent(field.getCode(), ignored -> new ArrayList<>()).add(new BoundFile(
                    field, file, ordinal.apply(row), contentVersion.apply(row), copy(evidenceDigest.apply(row))));
        }
        return result.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> entry.getValue().stream().sorted(Comparator.comparingInt(BoundFile::ordinal)).toList(),
                (left, right) -> left, LinkedHashMap::new));
    }

    private void markBound(TenantId tenantId, FileFieldDraftId draftId, List<FileFieldValue> values) {
        if (draftId == null) throw new IllegalArgumentException("file field draft is required");
        for (FileFieldValue value : values) {
            int changed = draftFileMapper.update(null, new LambdaUpdateWrapper<FileFieldDraftFilePO>()
                    .set(FileFieldDraftFilePO::getState, "BOUND")
                    .set(FileFieldDraftFilePO::getContentVersion, value.contentVersion())
                    .set(FileFieldDraftFilePO::getEvidenceDigest, value.evidenceDigest())
                    .eq(FileFieldDraftFilePO::getTenantId, tenantId.getValue())
                    .eq(FileFieldDraftFilePO::getDraftId, draftId.getValue())
                    .eq(FileFieldDraftFilePO::getFileId, value.fileId().getValue())
                    .eq(FileFieldDraftFilePO::getState, "STAGED"));
            if (changed != 1) throw new IllegalStateException("file field draft was already consumed");
        }
    }

    private boolean captureApprovalSnapshot(TenantId tenantId, ApprovalInstanceId instanceId) {
        if (approvalSnapshotMapper.selectCount(new LambdaQueryWrapper<ApprovalInstanceFileSnapshotPO>()
                .eq(ApprovalInstanceFileSnapshotPO::getTenantId, tenantId.getValue())
                .eq(ApprovalInstanceFileSnapshotPO::getInstanceId, instanceId.getValue())) > 0) {
            return false;
        }
        ApprovalInstanceFileSnapshotPO header = new ApprovalInstanceFileSnapshotPO();
        header.setTenantId(tenantId.getValue());
        header.setInstanceId(instanceId.getValue());
        if (approvalSnapshotMapper.insert(header) != 1) {
            throw new IllegalStateException("approval file snapshot insert failed");
        }
        return true;
    }

    private boolean captureStageSnapshot(TenantId tenantId, StageWorkflowRecordId recordId, String stageCode) {
        if (stageSnapshotMapper.selectCount(new LambdaQueryWrapper<StageWorkflowFileSnapshotPO>()
                .eq(StageWorkflowFileSnapshotPO::getTenantId, tenantId.getValue())
                .eq(StageWorkflowFileSnapshotPO::getRecordId, recordId.getValue())
                .eq(StageWorkflowFileSnapshotPO::getStageCode, stageCode)) > 0) {
            return false;
        }
        StageWorkflowFileSnapshotPO header = new StageWorkflowFileSnapshotPO();
        header.setTenantId(tenantId.getValue());
        header.setRecordId(recordId.getValue());
        header.setStageCode(stageCode);
        if (stageSnapshotMapper.insert(header) != 1) {
            throw new IllegalStateException("stage file snapshot insert failed");
        }
        return true;
    }

    private Map<Long, FieldDefinition> fields(TenantId tenantId, Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return fieldMapper.selectList(new LambdaQueryWrapper<CustomFieldDefinitionPO>()
                .eq(CustomFieldDefinitionPO::getTenantId, tenantId.getValue()).in(CustomFieldDefinitionPO::getId, ids))
                .stream().map(fieldConverter::toDomain).collect(Collectors.toMap(
                        value -> value.getId().getValue(), Function.identity()));
    }

    private Map<Long, AttachmentFile> files(TenantId tenantId, Set<Long> ids, boolean forUpdate) {
        if (ids.isEmpty()) return Map.of();
        return fileMapper.selectList(new LambdaQueryWrapper<AttachmentFilePO>()
                .eq(AttachmentFilePO::getTenantId, tenantId.getValue()).in(AttachmentFilePO::getId, ids)
                .last(forUpdate ? "FOR UPDATE" : "")).stream().map(fileConverter::toDomain)
                .collect(Collectors.toMap(value -> value.getId().getValue(), Function.identity()));
    }

    private long fieldId(TenantId tenantId, BusinessObjectType objectType, FieldCode code) {
        if (objectType == null || code == null) throw new IllegalArgumentException("file field code is required");
        CustomFieldDefinitionPO po = fieldMapper.selectOne(new LambdaQueryWrapper<CustomFieldDefinitionPO>()
                .eq(CustomFieldDefinitionPO::getTenantId, tenantId.getValue())
                .eq(CustomFieldDefinitionPO::getObjectType, objectType.name())
                .eq(CustomFieldDefinitionPO::getFieldCode, code.getValue()));
        if (po == null) throw new IllegalStateException("file field definition is unavailable");
        return po.getId();
    }

    private static Map<FieldCode, List<FileFieldValue>> grouped(List<FileFieldValue> values) {
        return values.stream().collect(Collectors.groupingBy(FileFieldValue::fieldCode,
                LinkedHashMap::new, Collectors.toList()));
    }

    private FileFieldDraftPO toPO(FileFieldDraft value) {
        FileFieldDraftPO po = new FileFieldDraftPO();
        po.setId(value.getId() == null ? null : value.getId().getValue());
        po.setTenantId(value.getTenantId().getValue());
        po.setUploaderActorId(value.getUploaderMemberId().getValue());
        po.setObjectType(value.getObjectType().name());
        po.setTargetType(value.getDirectTarget() == null ? null : value.getDirectTarget().type().name());
        po.setTargetId(value.getDirectTarget() == null ? null : value.getDirectTarget().id());
        po.setStageWorkflowRecordId(value.getStageWorkflowRecordId() == null ? null
                : value.getStageWorkflowRecordId().getValue());
        po.setOaFormId(value.getOaFormId() == null ? null : value.getOaFormId().getValue());
        po.setOaFormVersion(value.getOaFormVersion());
        po.setPurposeDigest(value.getPurposeDigest());
        po.setDeadline(value.getDeadline());
        po.setState(value.getState().name());
        po.setVersion(value.getVersion());
        po.setCreatedTime(value.getCreatedTime());
        po.setUpdatedTime(value.getUpdatedTime());
        return po;
    }

    private FileFieldDraft toDomain(FileFieldDraftPO po) {
        return FileFieldDraft.reconstitute(new FileFieldDraft.Snapshot(new FileFieldDraftId(po.getId()),
                new TenantId(po.getTenantId()), new io.github.archetom.crm.domain.valueobject.MemberId(po.getUploaderActorId()),
                BusinessObjectType.valueOf(po.getObjectType()), po.getTargetType() == null ? null
                        : new AttachmentTarget(AttachmentTargetType.valueOf(po.getTargetType()), po.getTargetId()),
                po.getStageWorkflowRecordId() == null ? null
                        : new io.github.archetom.crm.domain.valueobject.StageWorkflowRecordId(
                                po.getStageWorkflowRecordId()),
                po.getOaFormId() == null ? null : new OaFormId(po.getOaFormId()),
                po.getOaFormVersion(), po.getPurposeDigest(), po.getDeadline(),
                FileFieldDraftState.valueOf(po.getState()), po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
    }

    private static List<FileFieldValue> requireValues(List<FileFieldValue> values) {
        if (values == null) throw new IllegalArgumentException("file field values are required");
        if (values.stream().anyMatch(java.util.Objects::isNull)
                || values.stream().map(FileFieldValue::fileId).distinct().count() != values.size()) {
            throw new IllegalArgumentException("file field values are invalid");
        }
        return List.copyOf(values);
    }

    private static void requireTenant(TenantId tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("file field tenant is required");
    }

    private static void requireCrmTarget(AttachmentTarget target) {
        if (target == null || target.type() == null || target.type() == AttachmentTargetType.WORK_TASK
                || target.type() == AttachmentTargetType.OA_ANNOUNCEMENT
                || target.type() == AttachmentTargetType.OA_REQUEST) {
            throw new IllegalArgumentException("CRM file field target is required");
        }
    }

    private static void requireCrmTargetType(AttachmentTargetType targetType) {
        if (targetType == null || targetType == AttachmentTargetType.WORK_TASK
                || targetType == AttachmentTargetType.OA_ANNOUNCEMENT
                || targetType == AttachmentTargetType.OA_REQUEST) {
            throw new IllegalArgumentException("CRM file field target is required");
        }
    }

    private static Long targetId(AttachmentFieldBindingPO po, AttachmentTargetType targetType) {
        return switch (targetType) {
            case CUSTOMER -> po.getCustomerId();
            case CONTACT -> po.getContactId();
            case LEAD -> po.getLeadId();
            case OPPORTUNITY -> po.getOpportunityId();
            case CONTRACT -> po.getContractId();
            case INVOICE -> po.getInvoiceId();
            case RECEIVABLE -> po.getReceivableId();
            case RECEIVABLE_PLAN -> po.getReceivablePlanId();
            case PRODUCT -> po.getProductId();
            case RETURN_VISIT -> po.getReturnVisitId();
            default -> throw new IllegalArgumentException("CRM file field target is required");
        };
    }

    private static String targetColumn(AttachmentTargetType targetType) {
        return switch (targetType) {
            case CUSTOMER -> "customer_id";
            case CONTACT -> "contact_id";
            case LEAD -> "lead_id";
            case OPPORTUNITY -> "opportunity_id";
            case CONTRACT -> "contract_id";
            case INVOICE -> "invoice_id";
            case RECEIVABLE -> "receivable_id";
            case RECEIVABLE_PLAN -> "receivable_plan_id";
            case PRODUCT -> "product_id";
            case RETURN_VISIT -> "return_visit_id";
            default -> throw new IllegalArgumentException("CRM file field target is required");
        };
    }

    private static String targetColumn(AttachmentTarget target) {
        if (target.type() == AttachmentTargetType.CUSTOMER) return "customer_id";
        if (target.type() == AttachmentTargetType.CONTACT) return "contact_id";
        if (target.type() == AttachmentTargetType.LEAD) return "lead_id";
        if (target.type() == AttachmentTargetType.OPPORTUNITY) return "opportunity_id";
        if (target.type() == AttachmentTargetType.CONTRACT) return "contract_id";
        if (target.type() == AttachmentTargetType.INVOICE) return "invoice_id";
        if (target.type() == AttachmentTargetType.RECEIVABLE) return "receivable_id";
        if (target.type() == AttachmentTargetType.RECEIVABLE_PLAN) return "receivable_plan_id";
        if (target.type() == AttachmentTargetType.PRODUCT) return "product_id";
        if (target.type() == AttachmentTargetType.RETURN_VISIT) return "return_visit_id";
        throw new IllegalArgumentException("CRM file field target is required");
    }

    private static void setTarget(AttachmentFieldBindingPO po, AttachmentTarget target) {
        if (target.type() == AttachmentTargetType.CUSTOMER) { po.setCustomerId(target.id()); return; }
        if (target.type() == AttachmentTargetType.CONTACT) { po.setContactId(target.id()); return; }
        if (target.type() == AttachmentTargetType.LEAD) { po.setLeadId(target.id()); return; }
        if (target.type() == AttachmentTargetType.OPPORTUNITY) { po.setOpportunityId(target.id()); return; }
        if (target.type() == AttachmentTargetType.CONTRACT) { po.setContractId(target.id()); return; }
        if (target.type() == AttachmentTargetType.INVOICE) { po.setInvoiceId(target.id()); return; }
        if (target.type() == AttachmentTargetType.RECEIVABLE) { po.setReceivableId(target.id()); return; }
        if (target.type() == AttachmentTargetType.RECEIVABLE_PLAN) { po.setReceivablePlanId(target.id()); return; }
        if (target.type() == AttachmentTargetType.PRODUCT) { po.setProductId(target.id()); return; }
        if (target.type() == AttachmentTargetType.RETURN_VISIT) { po.setReturnVisitId(target.id()); return; }
        throw new IllegalArgumentException("CRM file field target is required");
    }

    private static byte[] copy(byte[] value) { return value == null ? null : Arrays.copyOf(value, value.length); }
}
