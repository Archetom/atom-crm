package io.github.archetom.crm.application.assembler;

import io.github.archetom.crm.application.vo.ApprovalConditionVO;
import io.github.archetom.crm.application.vo.ApprovalDefinitionVO;
import io.github.archetom.crm.application.vo.ApprovalInstanceVO;
import io.github.archetom.crm.application.vo.ApprovalInstanceSummaryVO;
import io.github.archetom.crm.application.vo.ApprovalStepVO;
import io.github.archetom.crm.application.vo.ApprovalTaskVO;
import io.github.archetom.crm.application.vo.AttachmentVO;
import io.github.archetom.crm.application.vo.ResolvedApprovalStepVO;
import io.github.archetom.crm.domain.entity.ApprovalDefinition;
import io.github.archetom.crm.domain.entity.ApprovalInstance;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.ApprovalInstanceSummary;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ResolvedApprovalStep;
import io.github.archetom.crm.domain.policy.OaRequestFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ApprovalAssembler {

    private ApprovalAssembler() {
    }

    public static ApprovalDefinitionVO toVO(ApprovalDefinition definition) {
        return new ApprovalDefinitionVO(definition.getId().getValue(),
                definition.getObjectType().name(), definition.getName(),
                definition.getDescription(), definition.getStatus().name(),
                definition.getRepeatMode().name(),
                definition.getInitiatorMemberIds().stream().map(value -> value.getValue()).toList(),
                definition.getInitiatorDepartmentIds().stream().map(value -> value.getValue()).toList(),
                definition.getManagerMemberIds().stream().map(value -> value.getValue()).toList(),
                definition.getSteps().stream().map(step -> new ApprovalStepVO(
                        step.code(), step.name(), step.order(), step.type().name(),
                        step.mode().name(), step.fallback().name(),
                        step.memberIds().stream().map(value -> value.getValue()).toList(),
                        step.roleIds().stream().map(value -> value.getValue()).toList(),
                        step.conditions().stream().map(condition -> new ApprovalConditionVO(
                                condition.fieldCode().getValue(), condition.operator().name(),
                                condition.values())).toList())).toList(),
                definition.getVersion(), definition.getCreatedTime(), definition.getUpdatedTime());
    }

    public static ApprovalInstanceVO toVO(
            ApprovalInstance instance,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            Set<FieldCode> hiddenRelationshipFacts,
            Set<String> allowedActions) {
        return toVO(instance, accesses, hiddenRelationshipFacts, Map.of(), allowedActions);
    }

    public static ApprovalInstanceVO toVO(
            ApprovalInstance instance,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            Set<FieldCode> hiddenRelationshipFacts,
            Map<String, List<AttachmentVO>> fileFields,
            Set<String> allowedActions) {
        Map<String, java.util.List<String>> facts = WorkflowSnapshotAssembler.visible(
                instance.getFacts(), accesses, hiddenRelationshipFacts);
        boolean oaRequest = instance.getObjectType() == BusinessObjectType.OA_REQUEST;
        return new ApprovalInstanceVO(instance.getId().getValue(),
                instance.getDefinitionId().getValue(), instance.getDefinitionVersion(),
                instance.getObjectType().name(), instance.getSubjectId(),
                instance.getIdempotencyKey(), oaRequest
                        ? visibleText(facts, OaRequestFieldCatalog.REQUEST_TITLE)
                        : instance.getTitle(), instance.getStatus().name(),
                instance.getCurrentStepOrder(), facts,
                fileFields == null ? Map.of() : fileFields,
                allowedActions == null ? List.of() : allowedActions.stream().sorted().toList(),
                instance.getTasks().stream().map(task -> new ApprovalTaskVO(
                        task.getTaskKey(), task.getSourceTaskKey(), task.getStepCode(),
                        task.getStepName(), task.getStepOrder(), task.getMode().name(),
                        task.getActorOrder(), null,
                        task.getStatus().name(), task.getRemark(), task.getActedTime())).toList(),
                instance.getVersion(), oaRequest
                        ? visibleTime(facts, OaRequestFieldCatalog.SUBMITTED_TIME)
                        : instance.getSubmittedTime(), instance.getCompletedTime(),
                instance.getUpdatedTime());
    }

    public static ApprovalInstanceSummaryVO toSummary(
            ApprovalInstanceSummary instance,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            Set<FieldCode> hiddenRelationshipFacts,
            Set<String> allowedActions) {
        Map<String, java.util.List<String>> facts = WorkflowSnapshotAssembler.visible(
                instance.facts(), accesses, hiddenRelationshipFacts);
        boolean oaRequest = instance.objectType() == BusinessObjectType.OA_REQUEST;
        return new ApprovalInstanceSummaryVO(instance.id().getValue(),
                instance.definitionId().getValue(), instance.definitionVersion(),
                instance.objectType().name(), instance.subjectId(),
                oaRequest ? visibleText(facts, OaRequestFieldCatalog.REQUEST_TITLE)
                        : instance.title(), instance.status().name(),
                instance.currentStepOrder(),
                allowedActions == null ? List.of() : allowedActions.stream().sorted().toList(),
                instance.version(),
                oaRequest ? visibleTime(facts, OaRequestFieldCatalog.SUBMITTED_TIME)
                        : instance.submittedTime(), instance.completedTime(),
                instance.updatedTime());
    }

    public static ApprovalInstanceSummaryVO toSummary(
            ApprovalInstance instance,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            Set<FieldCode> hiddenRelationshipFacts,
            Set<String> allowedActions) {
        return toSummary(ApprovalInstanceSummary.from(instance), accesses,
                hiddenRelationshipFacts, allowedActions);
    }

    public static ResolvedApprovalStepVO toVO(ResolvedApprovalStep step) {
        return new ResolvedApprovalStepVO(step.code(), step.name(), step.order(),
                step.mode().name(), step.assignees().stream().map(value -> value.getValue()).toList());
    }

    private static String visibleText(Map<String, List<String>> facts, String code) {
        List<String> values = facts.get(code);
        return values == null || values.size() != 1 ? null : values.getFirst();
    }

    private static LocalDateTime visibleTime(
            Map<String, List<String>> facts, String code) {
        try {
            String value = visibleText(facts, code);
            return value == null ? null : LocalDateTime.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
