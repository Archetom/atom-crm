package io.github.archetom.crm.application.vo;

import java.util.List;

/** Caller-authorized catalog used to build a complete stage-workflow replacement request. */
public record StageWorkflowConfigurationOptionsVO(
        List<StageWorkflowObjectTypeOptionVO> objectTypes,
        List<StageWorkflowFieldOptionVO> fields,
        List<StageWorkflowApprovalDefinitionOptionVO> approvalDefinitions) {
}
