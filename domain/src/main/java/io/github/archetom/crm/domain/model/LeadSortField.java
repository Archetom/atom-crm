package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.policy.LeadFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;

/** Server-owned lead fields that may safely participate in ordered pagination. */
public enum LeadSortField {
    UPDATED_TIME("updated_time", LeadFieldCatalog.UPDATED_TIME),
    CREATED_TIME("created_time", LeadFieldCatalog.CREATED_TIME),
    NAME("name", LeadFieldCatalog.NAME),
    OWNER_ACTOR("owner_actor_id", LeadFieldCatalog.OWNER_ACTOR);

    private final String column;
    private final FieldCode fieldCode;

    LeadSortField(String column, String fieldCode) {
        this.column = column;
        this.fieldCode = new FieldCode(fieldCode);
    }

    public String column() {
        return column;
    }

    public FieldCode fieldCode() {
        return fieldCode;
    }
}
