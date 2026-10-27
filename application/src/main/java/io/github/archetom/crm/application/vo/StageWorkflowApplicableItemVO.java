package io.github.archetom.crm.application.vo;

public record StageWorkflowApplicableItemVO(Long workflowId, String name,
                                             boolean canStart, String blockerCode) {
}
