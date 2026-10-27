package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record StageWorkflowRecordVO(
        Long id,
        Long workflowId,
        Long workflowVersion,
        String objectType,
        Long subjectId,
        String currentStageCode,
        StageCurrentStageVO currentStage,
        String status,
        Long pendingApprovalInstanceId,
        StageApprovalGateVO approvalGate,
        List<String> allowedActions,
        List<StageOutcomeOptionVO> allowedOutcomes,
        List<String> writableFieldCodes,
        List<StageWritableFieldVO> writableFields,
        List<String> allowedTaskCodes,
        List<StageProgressVO> progress,
        Map<String, Map<String, List<AttachmentVO>>> fileFields,
        String outcomeRemark,
        Long version,
        LocalDateTime completedTime,
        LocalDateTime updatedTime) {
}
