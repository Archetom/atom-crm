package io.github.archetom.crm.application.assembler;

import io.github.archetom.crm.application.vo.StageDefinitionVO;
import io.github.archetom.crm.application.vo.StageProgressVO;
import io.github.archetom.crm.application.vo.StageTaskVO;
import io.github.archetom.crm.application.vo.StageWorkflowCommentVO;
import io.github.archetom.crm.application.vo.StageWorkflowRecordVO;
import io.github.archetom.crm.application.vo.StageWorkflowVO;
import io.github.archetom.crm.application.vo.AttachmentVO;
import io.github.archetom.crm.application.vo.StageApprovalGateVO;
import io.github.archetom.crm.application.vo.StageCurrentStageVO;
import io.github.archetom.crm.application.vo.StageWritableFieldVO;
import io.github.archetom.crm.application.vo.StageOutcomeOptionVO;
import io.github.archetom.crm.domain.entity.StageWorkflowComment;
import io.github.archetom.crm.domain.entity.StageWorkflowDefinition;
import io.github.archetom.crm.domain.entity.StageWorkflowRecord;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.StageDefinition;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.util.Map;
import java.util.List;
import java.util.Set;

public final class StageWorkflowAssembler {

    private StageWorkflowAssembler() {
    }

    public static StageWorkflowVO toVO(StageWorkflowDefinition workflow) {
        return new StageWorkflowVO(workflow.getId().getValue(), workflow.getObjectType().name(),
                workflow.getName(), workflow.getSuccessName(), workflow.getFailureName(),
                workflow.getStatus().name(),
                workflow.getApplicableMemberIds().stream().map(value -> value.getValue()).toList(),
                workflow.getApplicableDepartmentIds().stream().map(value -> value.getValue()).toList(),
                workflow.getStages().stream().map(stage -> new StageDefinitionVO(
                        stage.code(), stage.name(), stage.order(), stage.probability(),
                        stage.requiredFieldCodes().stream().map(value -> value.getValue()).toList(),
                        stage.tasks().stream().map(task -> new StageTaskVO(
                                task.code(), task.name(), task.required())).toList(),
                        stage.approvalDefinitionId() == null ? null
                                : stage.approvalDefinitionId().getValue(), true, null)).toList(),
                workflow.getVersion(), workflow.getCreatedTime(), workflow.getUpdatedTime());
    }

    public static StageWorkflowRecordVO toVO(
            StageWorkflowRecord record,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        return toVO(record, accesses, Map.of(), null, Set.of(), List.of(), List.of(), List.of(),
                List.of(), null);
    }

    public static StageWorkflowRecordVO toVO(
            StageWorkflowRecord record,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            Map<String, Map<String, java.util.List<AttachmentVO>>> fileFields,
            StageDefinition currentStage,
            Set<String> allowedActions,
            List<StageOutcomeOptionVO> allowedOutcomes,
            List<String> writableFieldCodes,
            List<StageWritableFieldVO> writableFields,
            List<String> allowedTaskCodes,
            StageApprovalGateVO approvalGate) {
        return new StageWorkflowRecordVO(record.getId().getValue(),
                record.getWorkflowId().getValue(), record.getWorkflowVersion(),
                record.getObjectType().name(), record.getSubjectId(),
                record.getCurrentStageCode(), currentStage == null ? null : new StageCurrentStageVO(
                        currentStage.code(), currentStage.name(), currentStage.order(),
                        currentStage.tasks().stream().map(task -> new StageTaskVO(
                                task.code(), task.name(), task.required())).toList()),
                record.getStatus().name(),
                record.getPendingApprovalInstanceId() == null ? null
                        : record.getPendingApprovalInstanceId().getValue(),
                approvalGate,
                allowedActions == null ? List.of() : allowedActions.stream().sorted().toList(),
                allowedOutcomes == null ? List.of() : List.copyOf(allowedOutcomes),
                writableFieldCodes == null ? List.of() : List.copyOf(writableFieldCodes),
                writableFields == null ? List.of() : List.copyOf(writableFields),
                allowedTaskCodes == null ? List.of() : List.copyOf(allowedTaskCodes),
                record.getProgressByStage().values().stream().map(progress -> {
                    return new StageProgressVO(progress.stageCode(), progress.status().name(),
                            WorkflowSnapshotAssembler.visible(
                                    progress.fieldValues(), accesses),
                            progress.completedTaskCodes(),
                            progress.approvalAttempt(), progress.startedTime(),
                            progress.completedTime());
                }).toList(), fileFields == null ? Map.of() : fileFields,
                record.getOutcomeRemark(), record.getVersion(),
                record.getCompletedTime(), record.getUpdatedTime());
    }

    public static StageWorkflowCommentVO toVO(StageWorkflowComment comment) {
        return toVO(comment, null);
    }

    public static StageWorkflowCommentVO toVO(
            StageWorkflowComment comment, io.github.archetom.crm.domain.valueobject.MemberId caller) {
        return new StageWorkflowCommentVO(comment.getId().getValue(),
                comment.getWorkflowRecordId().getValue(), comment.getContent(),
                caller != null && caller.equals(comment.getAuthorMemberId()),
                comment.getVersion(), comment.getCreatedTime());
    }
}
