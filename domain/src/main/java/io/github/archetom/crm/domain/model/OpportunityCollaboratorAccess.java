package io.github.archetom.crm.domain.model;

/** Record-level access contributed by an explicit opportunity collaborator. */
public enum OpportunityCollaboratorAccess {
    READ,
    WRITE;

    public boolean canWrite() {
        return this == WRITE;
    }
}
