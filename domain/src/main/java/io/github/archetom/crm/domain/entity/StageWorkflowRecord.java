package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.StageWorkflowDomainException;
import io.github.archetom.crm.domain.model.ApprovalInstanceStatus;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.StageDefinition;
import io.github.archetom.crm.domain.model.StageProgress;
import io.github.archetom.crm.domain.model.StageWorkflowRecordStatus;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.StageWorkflowId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowRecordId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Versioned runtime state for one business record moving through a stage workflow. */
@Getter
public class StageWorkflowRecord extends AggregateRoot<StageWorkflowRecordId> {

    private StageWorkflowRecordId id;
    private TenantId tenantId;
    private StageWorkflowId workflowId;
    private Long workflowVersion;
    private BusinessObjectType objectType;
    private Long subjectId;
    private String currentStageCode;
    private StageWorkflowRecordStatus status;
    private ApprovalInstanceId pendingApprovalInstanceId;
    private Map<String, StageProgress> progressByStage = Map.of();
    private String outcomeRemark;
    private LocalDateTime completedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private StageWorkflowRecord() {
    }

    public record Snapshot(
            StageWorkflowRecordId id,
            TenantId tenantId,
            StageWorkflowId workflowId,
            Long workflowVersion,
            BusinessObjectType objectType,
            Long subjectId,
            String currentStageCode,
            StageWorkflowRecordStatus status,
            ApprovalInstanceId pendingApprovalInstanceId,
            Map<String, StageProgress> progressByStage,
            String outcomeRemark,
            Long version,
            LocalDateTime completedTime,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
    }

    public static StageWorkflowRecord start(
            TenantId tenantId,
            StageWorkflowId workflowId,
            Long workflowVersion,
            BusinessObjectType objectType,
            Long subjectId,
            StageDefinition firstStage) {
        StageWorkflowRecord record = new StageWorkflowRecord();
        record.tenantId = requireTenant(tenantId);
        record.workflowId = requireWorkflow(workflowId);
        record.workflowVersion = requireVersion(workflowVersion);
        record.objectType = requireObject(objectType);
        record.subjectId = requireSubject(subjectId);
        if (firstStage == null) {
            throw new StageWorkflowDomainException("first workflow stage is required");
        }
        record.currentStageCode = firstStage.code();
        record.status = StageWorkflowRecordStatus.ACTIVE;
        record.progressByStage = Map.of(firstStage.code(), StageProgress.start(firstStage.code()));
        LocalDateTime now = LocalDateTime.now();
        record.createdTime = now;
        record.updatedTime = now;
        return record;
    }

    public static StageWorkflowRecord reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new StageWorkflowDomainException("stage workflow record snapshot must not be empty");
        }
        StageWorkflowRecord record = new StageWorkflowRecord();
        record.id = snapshot.id();
        record.tenantId = requireTenant(snapshot.tenantId());
        record.workflowId = requireWorkflow(snapshot.workflowId());
        record.workflowVersion = requireVersion(snapshot.workflowVersion());
        record.objectType = requireObject(snapshot.objectType());
        record.subjectId = requireSubject(snapshot.subjectId());
        record.currentStageCode = snapshot.currentStageCode();
        record.status = snapshot.status() == null
                ? StageWorkflowRecordStatus.FAILED : snapshot.status();
        record.pendingApprovalInstanceId = snapshot.pendingApprovalInstanceId();
        record.progressByStage = copyProgress(snapshot.progressByStage());
        record.outcomeRemark = optional(snapshot.outcomeRemark(), 1000);
        record.completedTime = snapshot.completedTime();
        validateRuntime(record);
        record.restoreVersion(snapshot.version());
        record.createdTime = snapshot.createdTime();
        record.updatedTime = snapshot.updatedTime();
        return record;
    }

    public void capture(StageDefinition stage,
                        Map<FieldCode, List<String>> fieldValues,
                        Set<String> completedTaskCodes) {
        capture(stage, fieldValues, Set.of(), completedTaskCodes);
    }

    /** FILE values are persisted in typed evidence facts, not in scalar stage facts. */
    public void capture(StageDefinition stage,
                        Map<FieldCode, List<String>> fieldValues,
                        Set<FieldCode> satisfiedFileFields,
                        Set<String> completedTaskCodes) {
        ensureActive();
        requireCurrent(stage);
        StageProgress progress = progress();
        Map<FieldCode, List<String>> provided = fieldValues == null ? Map.of() : fieldValues;
        Set<FieldCode> satisfied = satisfiedFileFields == null ? Set.of() : Set.copyOf(satisfiedFileFields);
        if (stage.requiredFieldCodes().stream().anyMatch(code -> {
            List<String> values = provided.get(code);
            return (values == null || values.isEmpty()) && !satisfied.contains(code);
        })) {
            throw new StageWorkflowDomainException("required stage fields are missing");
        }
        Set<String> validTasks = stage.tasks().stream()
                .map(io.github.archetom.crm.domain.model.StageTaskDefinition::code)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> completed = completedTaskCodes == null ? Set.of()
                : java.util.Collections.unmodifiableSet(
                        new LinkedHashSet<>(completedTaskCodes));
        Set<String> requiredTasks = stage.tasks().stream().filter(
                        io.github.archetom.crm.domain.model.StageTaskDefinition::required)
                .map(io.github.archetom.crm.domain.model.StageTaskDefinition::code)
                .collect(java.util.stream.Collectors.toSet());
        if (!validTasks.containsAll(completed) || !completed.containsAll(requiredTasks)) {
            throw new StageWorkflowDomainException("stage task completion is invalid");
        }
        replaceProgress(progress.capture(provided, completed));
        updatedTime = LocalDateTime.now();
    }

    public int nextApprovalAttempt() {
        ensureActive();
        return progress().approvalAttempt() + 1;
    }

    public void awaitApproval(ApprovalInstanceId approvalInstanceId) {
        ensureActive();
        if (approvalInstanceId == null) {
            throw new StageWorkflowDomainException("stage approval instance is required");
        }
        replaceProgress(progress().awaitApproval());
        pendingApprovalInstanceId = approvalInstanceId;
        status = StageWorkflowRecordStatus.AWAITING_APPROVAL;
        updatedTime = LocalDateTime.now();
    }

    public boolean resolveApproval(ApprovalInstanceStatus approvalStatus) {
        if (status != StageWorkflowRecordStatus.AWAITING_APPROVAL
                || pendingApprovalInstanceId == null || approvalStatus == null
                || approvalStatus == ApprovalInstanceStatus.PENDING) {
            throw new StageWorkflowDomainException("stage approval is not resolved");
        }
        boolean approved = approvalStatus == ApprovalInstanceStatus.APPROVED;
        replaceProgress(progress().resume());
        pendingApprovalInstanceId = null;
        status = StageWorkflowRecordStatus.ACTIVE;
        updatedTime = LocalDateTime.now();
        return approved;
    }

    public void completeCurrent(StageDefinition current, StageDefinition next) {
        ensureActive();
        requireCurrent(current);
        StageProgress completed = progress().complete();
        replaceProgress(completed);
        if (next == null) {
            currentStageCode = null;
            status = StageWorkflowRecordStatus.SUCCEEDED;
            completedTime = LocalDateTime.now();
        } else {
            currentStageCode = next.code();
            LinkedHashMap<String, StageProgress> progress = new LinkedHashMap<>(progressByStage);
            progress.putIfAbsent(next.code(), StageProgress.start(next.code()));
            progressByStage = java.util.Collections.unmodifiableMap(progress);
        }
        updatedTime = LocalDateTime.now();
    }

    public void fail(String remark) {
        if (status == StageWorkflowRecordStatus.SUCCEEDED
                || status == StageWorkflowRecordStatus.FAILED) {
            throw new StageWorkflowDomainException("completed stage workflow cannot change outcome");
        }
        status = StageWorkflowRecordStatus.FAILED;
        pendingApprovalInstanceId = null;
        currentStageCode = null;
        outcomeRemark = optional(remark, 1000);
        completedTime = LocalDateTime.now();
        updatedTime = completedTime;
    }

    public void onPersisted(StageWorkflowRecordId persistedId, Long persistedVersion,
                            LocalDateTime created, LocalDateTime updated) {
        if (persistedId == null || id != null && !id.sameValueAs(persistedId)) {
            throw new StageWorkflowDomainException("persisted stage workflow record ID is invalid");
        }
        id = persistedId;
        restoreVersion(persistedVersion);
        if (created != null) {
            createdTime = created;
        }
        if (updated != null) {
            updatedTime = updated;
        }
    }

    private StageProgress progress() {
        StageProgress progress = progressByStage.get(currentStageCode);
        if (progress == null) {
            throw new StageWorkflowDomainException("current stage progress is missing");
        }
        return progress;
    }

    private void replaceProgress(StageProgress progress) {
        LinkedHashMap<String, StageProgress> values = new LinkedHashMap<>(progressByStage);
        values.put(progress.stageCode(), progress);
        progressByStage = java.util.Collections.unmodifiableMap(values);
    }

    private void requireCurrent(StageDefinition stage) {
        if (stage == null || currentStageCode == null || !currentStageCode.equals(stage.code())) {
            throw new StageWorkflowDomainException("stage is not current");
        }
    }

    private void ensureActive() {
        if (status != StageWorkflowRecordStatus.ACTIVE) {
            throw new StageWorkflowDomainException("stage workflow record is not active");
        }
    }

    private static Map<String, StageProgress> copyProgress(Map<String, StageProgress> source) {
        if (source == null || source.isEmpty() || source.size() > 50
                || source.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getValue() == null)) {
            throw new StageWorkflowDomainException("stored stage progress is invalid");
        }
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static void validateRuntime(StageWorkflowRecord record) {
        boolean awaiting = record.status == StageWorkflowRecordStatus.AWAITING_APPROVAL;
        boolean terminal = record.status == StageWorkflowRecordStatus.SUCCEEDED
                || record.status == StageWorkflowRecordStatus.FAILED;
        if (awaiting != (record.pendingApprovalInstanceId != null)
                || terminal != (record.currentStageCode == null)
                || terminal != (record.completedTime != null)) {
            throw new StageWorkflowDomainException("stored stage workflow state is invalid");
        }
        if (!terminal && !record.progressByStage.containsKey(record.currentStageCode)) {
            throw new StageWorkflowDomainException("stored current stage is missing");
        }
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new StageWorkflowDomainException("stage workflow tenant is required");
        }
        return value;
    }

    private static StageWorkflowId requireWorkflow(StageWorkflowId value) {
        if (value == null) {
            throw new StageWorkflowDomainException("stage workflow definition is required");
        }
        return value;
    }

    private static Long requireVersion(Long value) {
        if (value == null || value < 0) {
            throw new StageWorkflowDomainException("stage workflow version is invalid");
        }
        return value;
    }

    private static BusinessObjectType requireObject(BusinessObjectType value) {
        if (value == null) {
            throw new StageWorkflowDomainException("stage workflow object type is required");
        }
        return value;
    }

    private static Long requireSubject(Long value) {
        if (value == null || value <= 0) {
            throw new StageWorkflowDomainException("stage workflow subject ID must be positive");
        }
        return value;
    }

    private static String optional(String value, int maximum) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maximum) {
            throw new StageWorkflowDomainException("stage workflow remark is too long");
        }
        return normalized;
    }
}
