package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.StageWorkflowDomainException;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.util.LinkedHashSet;
import java.util.List;

/** Immutable metadata for one stage in a workflow definition. */
public record StageDefinition(
        String code,
        String name,
        int order,
        int probability,
        List<FieldCode> requiredFieldCodes,
        List<StageTaskDefinition> tasks,
        ApprovalDefinitionId approvalDefinitionId) {

    public static final int MAX_FIELDS = 100;
    public static final int MAX_TASKS = 100;

    public StageDefinition {
        code = StageTaskDefinition.normalizeCode(code, "stage code");
        name = StageTaskDefinition.normalizeText(name, 100, "stage name");
        if (order < 0 || probability < 0 || probability > 100) {
            throw new StageWorkflowDomainException("stage order or probability is invalid");
        }
        requiredFieldCodes = requiredFieldCodes == null ? List.of() : List.copyOf(requiredFieldCodes);
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        if (requiredFieldCodes.size() > MAX_FIELDS
                || requiredFieldCodes.stream().anyMatch(java.util.Objects::isNull)
                || new LinkedHashSet<>(requiredFieldCodes).size() != requiredFieldCodes.size()
                || tasks.size() > MAX_TASKS || tasks.stream().anyMatch(java.util.Objects::isNull)
                || new LinkedHashSet<>(tasks.stream().map(StageTaskDefinition::code).toList()).size()
                != tasks.size()) {
            throw new StageWorkflowDomainException("stage fields or tasks are invalid");
        }
    }
}
