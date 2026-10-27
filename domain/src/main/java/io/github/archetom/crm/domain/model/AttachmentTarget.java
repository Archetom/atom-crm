package io.github.archetom.crm.domain.model;

/** Exact target identity. Tenant identity is deliberately proven outside this value. */
public record AttachmentTarget(AttachmentTargetType type, Long id) {

    public AttachmentTarget {
        if (type == null || id == null || id <= 0) {
            throw new IllegalArgumentException("attachment target is invalid");
        }
    }
}
