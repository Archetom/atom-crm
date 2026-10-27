package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.exception.StageWorkflowDomainException;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable persisted progress snapshot for one stage. */
public record StageProgress(
        String stageCode,
        StageProgressStatus status,
        Map<FieldCode, List<String>> fieldValues,
        Set<String> completedTaskCodes,
        int approvalAttempt,
        LocalDateTime startedTime,
        LocalDateTime completedTime) {

    public StageProgress {
        stageCode = StageTaskDefinition.normalizeCode(stageCode, "stage progress code");
        if (status == null || approvalAttempt < 0) {
            throw new StageWorkflowDomainException("stage progress is invalid");
        }
        fieldValues = copyValues(fieldValues);
        completedTaskCodes = completedTaskCodes == null ? Set.of()
                : java.util.Collections.unmodifiableSet(new LinkedHashSet<>(completedTaskCodes));
        if (completedTaskCodes.size() > StageDefinition.MAX_TASKS
                || completedTaskCodes.stream().anyMatch(value -> value == null
                || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))) {
            throw new StageWorkflowDomainException("completed stage tasks are invalid");
        }
        if (status == StageProgressStatus.COMPLETED && completedTime == null
                || status != StageProgressStatus.COMPLETED && completedTime != null) {
            throw new StageWorkflowDomainException("stage completion time is invalid");
        }
    }

    public static StageProgress start(String stageCode) {
        return new StageProgress(stageCode, StageProgressStatus.ACTIVE,
                Map.of(), Set.of(), 0, LocalDateTime.now(), null);
    }

    public StageProgress capture(Map<FieldCode, List<String>> values,
                                 Set<String> completedTasks) {
        return new StageProgress(stageCode, StageProgressStatus.ACTIVE,
                values, completedTasks, approvalAttempt, startedTime, null);
    }

    public StageProgress awaitApproval() {
        return new StageProgress(stageCode, StageProgressStatus.AWAITING_APPROVAL,
                fieldValues, completedTaskCodes, approvalAttempt + 1, startedTime, null);
    }

    public StageProgress resume() {
        return new StageProgress(stageCode, StageProgressStatus.ACTIVE,
                fieldValues, completedTaskCodes, approvalAttempt, startedTime, null);
    }

    public StageProgress complete() {
        return new StageProgress(stageCode, StageProgressStatus.COMPLETED,
                fieldValues, completedTaskCodes, approvalAttempt,
                startedTime, LocalDateTime.now());
    }

    private static Map<FieldCode, List<String>> copyValues(Map<FieldCode, List<String>> source) {
        if (source == null) {
            return Map.of();
        }
        if (source.size() > StageDefinition.MAX_FIELDS
                || source.entrySet().stream().anyMatch(entry -> entry.getKey() == null)) {
            throw new StageWorkflowDomainException("stage field values are invalid");
        }
        LinkedHashMap<FieldCode, List<String>> copy = new LinkedHashMap<>();
        source.forEach((key, values) -> {
            if (values != null && values.stream().anyMatch(java.util.Objects::isNull)) {
                throw new StageWorkflowDomainException("stage field values are invalid");
            }
            List<String> list = values == null ? List.of() : List.copyOf(values);
            if (list.size() > FilterCondition.MAX_VALUES
                    || list.stream().anyMatch(value -> value == null || value.length() > 500)) {
                throw new StageWorkflowDomainException("stage field values are invalid");
            }
            copy.put(key, list);
        });
        return java.util.Collections.unmodifiableMap(copy);
    }
}
