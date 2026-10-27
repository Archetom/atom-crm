package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record StageWorkflowRecordResponse(
                                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long id,
                                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long workflowId,
                                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long workflowVersion,
                                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String objectType,
                                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long subjectId,
                                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long recordId,
                                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String currentStageCode,
                                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) StageCurrentStageResponse currentStage,
                                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
                                                  "ACTIVE", "AWAITING_APPROVAL", "SUCCEEDED", "FAILED"}) String status,
                                          Long pendingApprovalInstanceId,
                                          StageApprovalGateResponse approvalGate,
                                          @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                                                  schema = @Schema(allowableValues = {"ADVANCE", "FAIL", "COMMENT"}))
                                          List<String> allowedActions,
                                          @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                                                  schema = @Schema(implementation = StageOutcomeOptionResponse.class))
                                          List<StageOutcomeOptionResponse> allowedOutcomes,
                                          @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                                                  schema = @Schema(type = "string"))
                                          List<String> writableFieldCodes,
                                          @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                                                  schema = @Schema(implementation = StageWritableFieldResponse.class))
                                          List<StageWritableFieldResponse> writableFields,
                                          @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                                                  schema = @Schema(type = "string"))
                                          List<String> allowedTaskCodes,
                                          @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                                                  schema = @Schema(implementation = StageProgressResponse.class))
                                          List<StageProgressResponse> progress,
                                          Map<String, Map<String, List<StageFieldAttachmentResponse>>> fileFields,
                                          String outcomeRemark,
                                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") Long version,
                                          LocalDateTime completedTime,
                                          LocalDateTime updatedTime) {
}
