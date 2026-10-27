package io.github.archetom.crm.domain.model;

/** Record-level access contributed by an explicit customer collaborator. */
public enum CustomerCollaboratorAccess {
    READ,
    WRITE;

    public boolean canWrite() {
        return this == WRITE;
    }
}
