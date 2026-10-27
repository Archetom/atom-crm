package io.github.archetom.crm.domain.model;

/** Record-level access contributed by an explicit lead collaborator. */
public enum LeadCollaboratorAccess {
    READ,
    WRITE;

    public boolean canWrite() {
        return this == WRITE;
    }
}
