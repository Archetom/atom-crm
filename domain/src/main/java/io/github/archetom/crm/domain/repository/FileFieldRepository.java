package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FileFieldDraft;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.FileFieldValue;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FileFieldDraftId;
import io.github.archetom.crm.domain.valueobject.OaRequestId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowRecordId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Typed persistence port for B24c staging, CRM FILE values and immutable OA file facts. */
public interface FileFieldRepository {
    FileFieldDraft saveDraft(TenantId tenantId, FileFieldDraft draft);
    Optional<FileFieldDraft> findDraftForUpdate(TenantId tenantId, FileFieldDraftId draftId);
    Optional<FileFieldDraft> findDraft(TenantId tenantId, FileFieldDraftId draftId);
    Optional<FileFieldDraft> findDraftForStagedFile(TenantId tenantId, AttachmentFileId fileId);
    void stage(TenantId tenantId, FileFieldDraftId draftId, FieldDefinition field,
               AttachmentFileId fileId, int ordinal);
    List<StagedFile> findStagedFilesForUpdate(TenantId tenantId, FileFieldDraftId draftId);
    List<StagedFile> findStagedFiles(TenantId tenantId, FileFieldDraftId draftId);
    void bindCrm(TenantId tenantId, FileFieldDraftId draftId, AttachmentTarget target,
                 List<FileFieldValue> values);
    void replaceCrm(TenantId tenantId, AttachmentTarget target,
                    Map<FieldCode, List<FileFieldValue>> values);
    void withdrawCrmField(TenantId tenantId, AttachmentTarget target, FieldCode fieldCode);
    void bindOa(TenantId tenantId, FileFieldDraftId draftId, OaRequestId requestId,
                List<FileFieldValue> values);
    void snapshotApprovalInstance(TenantId tenantId, ApprovalInstanceId instanceId,
                                  BusinessObjectType objectType,
                                  Map<FieldCode, List<FileFieldValue>> values);
    void snapshotStageWorkflowRecord(TenantId tenantId, StageWorkflowRecordId recordId,
                                     BusinessObjectType objectType, String stageCode,
                                     Map<FieldCode, List<FileFieldValue>> values);
    void markStagedBound(TenantId tenantId, FileFieldDraftId draftId, List<FileFieldValue> values);
    void withdrawStagedFiles(TenantId tenantId, FileFieldDraftId draftId);
    Map<FieldCode, List<BoundFile>> findCrmValues(TenantId tenantId, AttachmentTarget target);

    /**
     * Loads the FILE values of many targets of one type in a single query, keyed by target id.
     * A list projection needs every row's values at once; asking per row turns one page into one
     * query per row.
     */
    Map<Long, Map<FieldCode, List<BoundFile>>> findCrmValues(
            TenantId tenantId, AttachmentTargetType targetType, Collection<Long> targetIds);
    Map<FieldCode, List<BoundFile>> findOaValues(TenantId tenantId, OaRequestId requestId);

    /** Loads the immutable OA FILE facts of many requests in a single query, keyed by request id. */
    Map<Long, Map<FieldCode, List<BoundFile>>> findOaValues(
            TenantId tenantId, Collection<OaRequestId> requestIds);
    Map<FieldCode, List<BoundFile>> findApprovalInstanceValues(TenantId tenantId,
                                                                ApprovalInstanceId instanceId);
    Map<String, Map<FieldCode, List<BoundFile>>> findStageWorkflowValues(TenantId tenantId,
                                                                           StageWorkflowRecordId recordId);
    boolean hasCurrentDependency(TenantId tenantId, FieldDefinition field);

    record StagedFile(FieldDefinition field, AttachmentFile file, int ordinal) { }
    record BoundFile(FieldDefinition field, AttachmentFile file, int ordinal,
                     long contentVersion, byte[] evidenceDigest) { }
}
