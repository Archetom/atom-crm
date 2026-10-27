package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.service.FileFieldBindingService;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FileFieldDraft;
import io.github.archetom.crm.domain.model.AttachmentByteState;
import io.github.archetom.crm.domain.model.AttachmentInspectionState;
import io.github.archetom.crm.domain.model.AttachmentPublicationState;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FileFieldValue;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.repository.FileFieldRepository;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FileFieldDraftId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaFormId;
import io.github.archetom.crm.domain.valueobject.OaRequestId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowRecordId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Enforces B24c cardinality, field policy and exact clean-content evidence before a bind. */
@Service
@RequiredArgsConstructor
public class FileFieldBindingServiceImpl implements FileFieldBindingService {
    private final FileFieldRepository fileFieldRepository;
    private final AttachmentFileRepository attachmentFileRepository;
    private final AttachmentWorkStore workStore;
    private final AttachmentRuntimePolicy runtimePolicy;
    private final Clock clock;

    @Override
    public void consumeCreateDraft(TenantId tenantId, MemberId uploader, BusinessObjectType objectType,
                                   Long rawDraftId, AttachmentTarget target,
                                   Map<String, List<Long>> fileFields,
                                   Map<FieldCode, FieldDefinition> definitions,
                                   Map<FieldCode, EffectiveFieldAccess> accesses) {
        consumeDraft(tenantId, uploader, objectType, null, rawDraftId, target, fileFields,
                definitions, accesses);
    }

    @Override
    public void consumeStageWorkflowDraft(TenantId tenantId, MemberId uploader,
                                          BusinessObjectType objectType,
                                          StageWorkflowRecordId workflowRecordId, Long rawDraftId,
                                          AttachmentTarget target, Map<String, List<Long>> fileFields,
                                          Map<FieldCode, FieldDefinition> definitions,
                                          Map<FieldCode, EffectiveFieldAccess> accesses) {
        if (workflowRecordId == null) {
            throw invalid("stage workflow record is required for a file field draft");
        }
        consumeDraft(tenantId, uploader, objectType, workflowRecordId, rawDraftId, target, fileFields,
                definitions, accesses);
    }

    private void consumeDraft(TenantId tenantId, MemberId uploader, BusinessObjectType objectType,
                              StageWorkflowRecordId workflowRecordId, Long rawDraftId,
                              AttachmentTarget target, Map<String, List<Long>> fileFields,
                              Map<FieldCode, FieldDefinition> definitions,
                              Map<FieldCode, EffectiveFieldAccess> accesses) {
        Map<FieldCode, List<Long>> requested = requested(fileFields, definitions, accesses, true);
        if (rawDraftId == null) {
            requireRequired(definitions, requested, Map.of());
            if (!requested.isEmpty()) throw invalid("file field draft is required");
            return;
        }
        FileFieldDraftId draftId = id(rawDraftId);
        LocalDateTime now = now();
        FileFieldDraft draft = fileFieldRepository.findDraftForUpdate(tenantId, draftId)
                .orElseThrow(() -> invalid("file field draft is unavailable"));
        boolean permitted = workflowRecordId == null
                ? draft.getDirectTarget() == null && draft.getStageWorkflowRecordId() == null
                        && draft.isActiveFor(uploader, objectType, now)
                : draft.getDirectTarget() == null && draft.isStageWorkflowDraftFor(
                        uploader, objectType, workflowRecordId, now);
        if (!permitted) {
            throw invalid("file field draft is unavailable");
        }
        List<FileFieldRepository.StagedFile> staged = fileFieldRepository.findStagedFilesForUpdate(tenantId, draftId);
        List<FileFieldValue> values = values(tenantId, requested, staged);
        requireRequired(definitions, requested, Map.of());
        publish(tenantId, values, staged, now);
        fileFieldRepository.bindCrm(tenantId, draftId, target, values);
        draft.consume(uploader, objectType, now);
        fileFieldRepository.saveDraft(tenantId, draft);
    }

    @Override
    public void replaceExisting(TenantId tenantId, MemberId uploader, BusinessObjectType objectType,
                                AttachmentTarget target, Map<String, List<Long>> fileFields,
                                Map<FieldCode, FieldDefinition> definitions,
                                Map<FieldCode, EffectiveFieldAccess> accesses) {
        if (fileFields == null) return;
        Map<FieldCode, List<Long>> requested = requested(fileFields, definitions, accesses, false);
        Map<FieldCode, List<FileFieldRepository.BoundFile>> current = fileFieldRepository.findCrmValues(tenantId, target);
        requireRequired(definitions, requested, current);
        if (requested.isEmpty()) return;
        LinkedHashMap<FileFieldDraftId, List<FileFieldValue>> perDraft = new LinkedHashMap<>();
        LinkedHashMap<FieldCode, List<FileFieldValue>> desired = new LinkedHashMap<>();
        Map<AttachmentFileId, FileFieldRepository.BoundFile> currentByFile = current.values().stream()
                .flatMap(Collection::stream).collect(Collectors.toMap(value -> value.file().getId(), value -> value));
        Set<AttachmentFileId> selected = new LinkedHashSet<>();
        List<FileFieldValue> candidatesToPublish = new ArrayList<>();
        List<FileFieldRepository.StagedFile> candidates = new ArrayList<>();
        for (Map.Entry<FieldCode, List<Long>> entry : requested.entrySet()) {
            List<FileFieldValue> fieldValues = new ArrayList<>();
            int ordinal = 0;
            for (Long rawFileId : entry.getValue()) {
                AttachmentFileId fileId = attachmentId(rawFileId);
                if (!selected.add(fileId)) throw invalid("file field candidate is duplicated");
                FileFieldRepository.BoundFile bound = currentByFile.get(fileId);
                if (bound != null) {
                    if (!bound.field().getCode().equals(entry.getKey())) {
                        throw invalid("bound file does not match its field");
                    }
                    fieldValues.add(new FileFieldValue(entry.getKey(), fileId, ordinal++,
                            bound.contentVersion(), bound.evidenceDigest()));
                    continue;
                }
                FileFieldDraft routed = fileFieldRepository.findDraftForStagedFile(tenantId, fileId)
                        .orElseThrow(() -> invalid("file field candidate is unavailable"));
                FileFieldDraft draft = fileFieldRepository.findDraftForUpdate(tenantId, routed.getId())
                        .orElseThrow(() -> invalid("file field candidate is unavailable"));
                if (draft.getDirectTarget() == null || draft.getStageWorkflowRecordId() != null
                        || !draft.getDirectTarget().equals(target)
                        || !draft.isActiveFor(uploader, objectType, now())) {
                    throw invalid("file field candidate is unavailable");
                }
                FileFieldRepository.StagedFile staged = fileFieldRepository.findStagedFilesForUpdate(tenantId,
                                draft.getId()).stream().filter(value -> value.file().getId().sameValueAs(fileId))
                        .findFirst().orElseThrow(() -> invalid("file field candidate is unavailable"));
                if (!staged.field().getCode().equals(entry.getKey())) {
                    throw invalid("file field candidate does not match its field");
                }
                FileFieldValue value = value(tenantId, entry.getKey(), staged.file(), ordinal++);
                fieldValues.add(value); candidatesToPublish.add(value); candidates.add(staged);
                perDraft.computeIfAbsent(draft.getId(), ignored -> new ArrayList<>()).add(value);
            }
            desired.put(entry.getKey(), List.copyOf(fieldValues));
        }
        withdrawRemoved(tenantId, target, requested.keySet(), current, selected, now());
        publish(tenantId, candidatesToPublish, candidates, now());
        fileFieldRepository.replaceCrm(tenantId, target, desired);
        for (Map.Entry<FileFieldDraftId, List<FileFieldValue>> entry : perDraft.entrySet()) {
            fileFieldRepository.markStagedBound(tenantId, entry.getKey(), entry.getValue());
            FileFieldDraft draft = fileFieldRepository.findDraftForUpdate(tenantId, entry.getKey())
                    .orElseThrow(() -> new IllegalStateException("file field draft disappeared"));
            draft.consume(uploader, objectType, now());
            fileFieldRepository.saveDraft(tenantId, draft);
        }
    }

    @Override
    public void withdrawAll(TenantId tenantId, AttachmentTarget target) {
        Map<FieldCode, List<FileFieldRepository.BoundFile>> current =
                fileFieldRepository.findCrmValues(tenantId, target);
        withdrawRemoved(tenantId, target, current.keySet(), current, Set.of(), now());
        current.keySet().forEach(code -> fileFieldRepository.withdrawCrmField(tenantId, target, code));
    }

    @Override
    public void consumeOaDraft(TenantId tenantId, MemberId uploader, OaFormId formId, Long formVersion,
                               Long rawDraftId, OaRequestId requestId,
                               Map<String, List<Long>> fileFields,
                               Map<FieldCode, FieldDefinition> definitions,
                               Map<FieldCode, EffectiveFieldAccess> accesses) {
        Map<FieldCode, List<Long>> requested = requested(fileFields, definitions, accesses, true);
        if (rawDraftId == null) {
            requireRequired(definitions, requested, Map.of());
            if (!requested.isEmpty()) throw invalid("file field draft is required");
            return;
        }
        FileFieldDraftId draftId = id(rawDraftId);
        LocalDateTime now = now();
        FileFieldDraft draft = fileFieldRepository.findDraftForUpdate(tenantId, draftId)
                .orElseThrow(() -> invalid("file field draft is unavailable"));
        if (!draft.isActiveFor(uploader, BusinessObjectType.OA_REQUEST, now)
                || draft.getDirectTarget() != null || draft.getOaFormId() == null
                || !draft.getOaFormId().sameValueAs(formId)
                || !java.util.Objects.equals(draft.getOaFormVersion(), formVersion)) {
            throw invalid("file field draft is unavailable");
        }
        List<FileFieldRepository.StagedFile> staged = fileFieldRepository.findStagedFilesForUpdate(tenantId, draftId);
        List<FileFieldValue> values = values(tenantId, requested, staged);
        requireRequired(definitions, requested, Map.of());
        publish(tenantId, values, staged, now);
        fileFieldRepository.bindOa(tenantId, draftId, requestId, values);
        draft.consume(uploader, BusinessObjectType.OA_REQUEST, now);
        fileFieldRepository.saveDraft(tenantId, draft);
    }

    @Override
    public Map<FieldCode, List<FileFieldValue>> prepareOaSubmission(
            TenantId tenantId, MemberId uploader, OaFormId formId, Long formVersion, Long rawDraftId,
            Map<String, List<Long>> fileFields, Map<FieldCode, FieldDefinition> definitions,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        return prepareOa(tenantId, uploader, formId, formVersion, rawDraftId, fileFields,
                definitions, accesses, true);
    }

    @Override
    public Map<FieldCode, List<FileFieldValue>> prepareOaPreview(
            TenantId tenantId, MemberId uploader, OaFormId formId, Long formVersion, Long rawDraftId,
            Map<String, List<Long>> fileFields, Map<FieldCode, FieldDefinition> definitions,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        return prepareOa(tenantId, uploader, formId, formVersion, rawDraftId, fileFields,
                definitions, accesses, false);
    }

    private Map<FieldCode, List<FileFieldValue>> prepareOa(
            TenantId tenantId, MemberId uploader, OaFormId formId, Long formVersion, Long rawDraftId,
            Map<String, List<Long>> fileFields, Map<FieldCode, FieldDefinition> definitions,
            Map<FieldCode, EffectiveFieldAccess> accesses, boolean forUpdate) {
        Map<FieldCode, List<Long>> requested = requested(fileFields, definitions, accesses, true);
        if (rawDraftId == null) {
            requireRequired(definitions, requested, Map.of());
            if (!requested.isEmpty()) throw invalid("file field draft is required");
            return Map.of();
        }
        FileFieldDraft draft = (forUpdate ? fileFieldRepository.findDraftForUpdate(tenantId, id(rawDraftId))
                : fileFieldRepository.findDraft(tenantId, id(rawDraftId)))
                .orElseThrow(() -> invalid("file field draft is unavailable"));
        if (!draft.isActiveFor(uploader, BusinessObjectType.OA_REQUEST, now())
                || draft.getDirectTarget() != null || draft.getOaFormId() == null
                || !draft.getOaFormId().sameValueAs(formId)
                || !java.util.Objects.equals(draft.getOaFormVersion(), formVersion)) {
            throw invalid("file field draft is unavailable");
        }
        List<FileFieldValue> values = values(tenantId, requested, forUpdate
                ? fileFieldRepository.findStagedFilesForUpdate(tenantId, draft.getId())
                : fileFieldRepository.findStagedFiles(tenantId, draft.getId()));
        requireRequired(definitions, requested, Map.of());
        return grouped(values);
    }

    private Map<FieldCode, List<Long>> requested(Map<String, List<Long>> source,
            Map<FieldCode, FieldDefinition> definitions,
            Map<FieldCode, EffectiveFieldAccess> accesses, boolean create) {
        Map<String, List<Long>> raw = source == null ? Map.of() : source;
        LinkedHashMap<FieldCode, List<Long>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Long>> entry : raw.entrySet()) {
            FieldCode code;
            try { code = new FieldCode(entry.getKey()); }
            catch (RuntimeException exception) { throw invalid("file field code is invalid"); }
            FieldDefinition definition = definitions == null ? null : definitions.get(code);
            EffectiveFieldAccess access = accesses == null ? null : accesses.get(code);
            if (definition == null || !definition.isActive() || definition.getFieldType() != CustomFieldType.FILE
                    || access == null || !access.accessLevel().covers(FieldAccessLevel.WRITE)) {
                throw invalid("file field is unavailable");
            }
            List<Long> files = entry.getValue() == null ? List.of() : List.copyOf(entry.getValue());
            if (files.size() > 10 || files.stream().anyMatch(value -> value == null || value <= 0)
                    || new LinkedHashSet<>(files).size() != files.size()) {
                throw invalid("file field values are invalid");
            }
            result.put(code, files);
        }
        return result;
    }

    private List<FileFieldValue> values(TenantId tenantId, Map<FieldCode, List<Long>> requested,
            List<FileFieldRepository.StagedFile> staged) {
        Map<AttachmentFileId, FileFieldRepository.StagedFile> byId = staged.stream()
                .collect(Collectors.toMap(value -> value.file().getId(), value -> value));
        List<FileFieldValue> result = new ArrayList<>();
        Set<AttachmentFileId> selected = new LinkedHashSet<>();
        for (Map.Entry<FieldCode, List<Long>> entry : requested.entrySet()) {
            int ordinal = 0;
            for (Long rawId : entry.getValue()) {
                AttachmentFileId id = attachmentId(rawId);
                FileFieldRepository.StagedFile value = byId.get(id);
                if (value == null || !value.field().getCode().equals(entry.getKey())) {
                    throw invalid("file field draft does not contain the selected file");
                }
                selected.add(id);
                result.add(value(tenantId, entry.getKey(), value.file(), ordinal++));
            }
        }
        if (selected.size() != staged.size()) {
            throw invalid("file field draft must be consumed as an exact set");
        }
        return result;
    }

    private FileFieldValue value(TenantId tenantId, FieldCode code, AttachmentFile file, int ordinal) {
        if (file == null || file.getId() == null || file.getByteState() != AttachmentByteState.STORED
                || file.getInspectionState() != AttachmentInspectionState.CLEAN
                || file.getPublicationState() != AttachmentPublicationState.PRIVATE) {
            throw invalid("file field candidate is not clean and ready");
        }
        AttachmentWorkStore.ContentPlan plan = workStore.findContentPlan(tenantId, file.getId())
                .filter(value -> value.contentVersion() == file.getContentVersion()
                        && value.evidence() != null).orElseThrow(() -> invalid("file field content is unavailable"));
        return new FileFieldValue(code, file.getId(), ordinal, file.getContentVersion(),
                plan.evidence().sha256());
    }

    private void publish(TenantId tenantId, List<FileFieldValue> values,
                         List<FileFieldRepository.StagedFile> staged, LocalDateTime now) {
        Map<AttachmentFileId, AttachmentFile> files = staged.stream().collect(Collectors.toMap(
                value -> value.file().getId(), FileFieldRepository.StagedFile::file));
        for (FileFieldValue value : values) {
            AttachmentFile file = files.get(value.fileId());
            if (file == null) {
                file = attachmentFileRepository.findByIdForUpdate(tenantId, value.fileId())
                        .orElseThrow(() -> invalid("file field candidate is unavailable"));
            }
            file.publish(now);
            attachmentFileRepository.save(tenantId, file);
        }
    }

    private void withdrawRemoved(TenantId tenantId, AttachmentTarget target, Collection<FieldCode> fields,
                                 Map<FieldCode, List<FileFieldRepository.BoundFile>> current,
                                 Set<AttachmentFileId> retained, LocalDateTime now) {
        for (FieldCode code : fields) {
            for (FileFieldRepository.BoundFile old : current.getOrDefault(code, List.of())) {
                if (retained.contains(old.file().getId())) continue;
                AttachmentFile file = attachmentFileRepository.findByIdForUpdate(tenantId, old.file().getId())
                        .orElseThrow(() -> new IllegalStateException("bound file disappeared"));
                file.withdraw(now);
                attachmentFileRepository.save(tenantId, file);
                Instant instant = now.toInstant(ZoneOffset.UTC);
                AttachmentWorkStore.JobKind cleanup = file.getByteState() == AttachmentByteState.REMOVING
                        ? AttachmentWorkStore.JobKind.DELETION : AttachmentWorkStore.JobKind.INCOMPLETE_ABORT;
                Instant deadline = instant.plus(runtimePolicy.tombstoneRetention());
                workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(tenantId, file.getId(),
                        file.getContentVersion(), cleanup, 1, instant, deadline));
                workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(tenantId, file.getId(),
                        file.getContentVersion(), AttachmentWorkStore.JobKind.RETENTION_SWEEP,
                        3, deadline, deadline.plus(runtimePolicy.tombstoneRetention())));
            }
        }
    }

    private void requireRequired(Map<FieldCode, FieldDefinition> definitions,
                                 Map<FieldCode, List<Long>> requested,
                                 Map<FieldCode, List<FileFieldRepository.BoundFile>> current) {
        if (definitions == null) return;
        for (FieldDefinition definition : definitions.values()) {
            if (definition.isActive() && definition.getFieldType() == CustomFieldType.FILE
                    && definition.isRequired()) {
                List<Long> value = requested.get(definition.getCode());
                boolean retained = !requested.containsKey(definition.getCode())
                        && !current.getOrDefault(definition.getCode(), List.of()).isEmpty();
                if ((value == null || value.isEmpty()) && !retained) {
                    throw invalid("required file field is missing");
                }
            }
        }
    }

    private static Map<FieldCode, List<FileFieldValue>> grouped(List<FileFieldValue> values) {
        LinkedHashMap<FieldCode, List<FileFieldValue>> result = new LinkedHashMap<>();
        for (FileFieldValue value : values) {
            result.computeIfAbsent(value.fieldCode(), ignored -> new ArrayList<>()).add(value);
        }
        result.replaceAll((ignored, value) -> List.copyOf(value));
        return Map.copyOf(result);
    }

    private static FileFieldDraftId id(Long value) {
        try { return new FileFieldDraftId(value); }
        catch (RuntimeException exception) { throw invalid("file field draft ID is invalid"); }
    }
    private static AttachmentFileId attachmentId(Long value) {
        try { return new AttachmentFileId(value); }
        catch (RuntimeException exception) { throw invalid("file field candidate is invalid"); }
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private static NonRetryableApplicationException invalid(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID, message);
    }
}
