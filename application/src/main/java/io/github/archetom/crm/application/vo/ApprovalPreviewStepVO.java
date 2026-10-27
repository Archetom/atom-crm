package io.github.archetom.crm.application.vo;

/** Server-calculated preview step capability. */
public record ApprovalPreviewStepVO(String code, String name, boolean selectionRequired) {
}
