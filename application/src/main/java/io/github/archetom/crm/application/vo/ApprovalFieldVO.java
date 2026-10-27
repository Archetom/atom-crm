package io.github.archetom.crm.application.vo;

public record ApprovalFieldVO(
        String fieldCode,
        String displayName,
        String fieldType,
        boolean required) {
}
