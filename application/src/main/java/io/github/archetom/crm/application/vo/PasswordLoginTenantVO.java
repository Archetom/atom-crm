package io.github.archetom.crm.application.vo;

/** One active tenant membership available after credential verification. */
public record PasswordLoginTenantVO(Long tenantId, Long departmentId) {
}
