package io.github.archetom.crm.domain.model;

import java.time.LocalDateTime;

public record WorklistItemFingerprint(
        String kind, Long sourceId, Long sourceVersion, LocalDateTime dueTime) {
    public WorklistItemFingerprint {
        kind = kind == null ? null : kind.trim();
        if (kind == null || !kind.matches("[A-Z][A-Z0-9_]{0,39}")
                || sourceId == null || sourceId <= 0 || sourceVersion == null
                || sourceVersion < 0 || dueTime == null) {
            throw new IllegalArgumentException("worklist item fingerprint is invalid");
        }
    }
}
