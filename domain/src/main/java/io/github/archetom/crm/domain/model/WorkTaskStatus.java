package io.github.archetom.crm.domain.model;

public enum WorkTaskStatus {
    TODO,
    IN_PROGRESS,
    DONE,
    CANCELLED,
    DELETED;

    public boolean isOpen() {
        return this == TODO || this == IN_PROGRESS;
    }
}
