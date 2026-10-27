package io.github.archetom.crm.application.service;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FileFieldValue;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaFormId;
import io.github.archetom.crm.domain.valueobject.OaRequestId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowRecordId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Map;

/** Owns atomic consumption/replacement of structured CRM FILE values. */
public interface FileFieldBindingService {
    void consumeCreateDraft(TenantId tenantId, MemberId uploader, BusinessObjectType objectType,
                            Long draftId, AttachmentTarget target,
                            Map<String, List<Long>> fileFields,
                            Map<io.github.archetom.crm.domain.valueobject.FieldCode, FieldDefinition> definitions,
                            Map<io.github.archetom.crm.domain.valueobject.FieldCode, EffectiveFieldAccess> accesses);

    /** Consumes a FILE draft issued for exactly one active stage workflow record. */
    void consumeStageWorkflowDraft(TenantId tenantId, MemberId uploader, BusinessObjectType objectType,
                                   StageWorkflowRecordId workflowRecordId, Long draftId,
                                   AttachmentTarget target, Map<String, List<Long>> fileFields,
                                   Map<io.github.archetom.crm.domain.valueobject.FieldCode, FieldDefinition> definitions,
                                   Map<io.github.archetom.crm.domain.valueobject.FieldCode, EffectiveFieldAccess> accesses);

    void replaceExisting(TenantId tenantId, MemberId uploader, BusinessObjectType objectType,
                         AttachmentTarget target, Map<String, List<Long>> fileFields,
                         Map<io.github.archetom.crm.domain.valueobject.FieldCode, FieldDefinition> definitions,
                         Map<io.github.archetom.crm.domain.valueobject.FieldCode, EffectiveFieldAccess> accesses);

    /** Withdraws every FILE value when its CRM parent becomes unavailable. */
    void withdrawAll(TenantId tenantId, AttachmentTarget target);

    void consumeOaDraft(TenantId tenantId, MemberId uploader, OaFormId formId, Long formVersion,
                        Long draftId, OaRequestId requestId, Map<String, List<Long>> fileFields,
                        Map<io.github.archetom.crm.domain.valueobject.FieldCode, FieldDefinition> definitions,
                        Map<io.github.archetom.crm.domain.valueobject.FieldCode, EffectiveFieldAccess> accesses);

    Map<io.github.archetom.crm.domain.valueobject.FieldCode, List<FileFieldValue>> prepareOaSubmission(
            TenantId tenantId, MemberId uploader, OaFormId formId, Long formVersion, Long draftId,
            Map<String, List<Long>> fileFields,
            Map<io.github.archetom.crm.domain.valueobject.FieldCode, FieldDefinition> definitions,
            Map<io.github.archetom.crm.domain.valueobject.FieldCode, EffectiveFieldAccess> accesses);

    /** Validates the current OA FILE draft without consuming it or taking a write lock. */
    Map<io.github.archetom.crm.domain.valueobject.FieldCode, List<FileFieldValue>> prepareOaPreview(
            TenantId tenantId, MemberId uploader, OaFormId formId, Long formVersion, Long draftId,
            Map<String, List<Long>> fileFields,
            Map<io.github.archetom.crm.domain.valueobject.FieldCode, FieldDefinition> definitions,
            Map<io.github.archetom.crm.domain.valueobject.FieldCode, EffectiveFieldAccess> accesses);
}
