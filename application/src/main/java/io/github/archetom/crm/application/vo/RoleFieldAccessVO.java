package io.github.archetom.crm.application.vo;

public record RoleFieldAccessVO(Long fieldId, String fieldCode, String displayName,
                                String accessLevel, String maskType) {
}
