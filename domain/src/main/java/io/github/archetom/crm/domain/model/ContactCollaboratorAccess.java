package io.github.archetom.crm.domain.model;

/** Record-level access contributed by an explicit contact collaborator. */
public enum ContactCollaboratorAccess {
    READ,
    WRITE;

    public boolean canWrite() {
        return this == WRITE;
    }
}
