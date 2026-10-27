package io.github.archetom.crm.domain.model;

/** Lifecycle for a Work-owned future-task template. */
public enum WorkTaskSeriesStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    ENDED,
    DELETED;

    public boolean isVisible() {
        return this != DELETED;
    }
}
