package io.github.archetom.crm.application.vo;

/** Code-defined capability available to a tenant role editor. */
public record AuthorityCapabilityVO(
        String code,
        String displayName,
        boolean supportsDataScope
) {
}
