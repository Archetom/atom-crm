package io.github.archetom.crm.application.vo;

public record StageWorkflowFieldOptionVO(String code, String displayName, String fieldType,
                                         boolean writable, boolean requiredFieldEligible,
                                         String blockerCode) {
}
