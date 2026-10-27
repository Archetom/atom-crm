package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.policy.ContactFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;

/** Server-owned contact fields that may safely participate in ordered pagination. */
public enum ContactSortField {
    UPDATED_TIME("updated_time", ContactFieldCatalog.UPDATED_TIME),
    CREATED_TIME("created_time", ContactFieldCatalog.CREATED_TIME),
    NAME("name", ContactFieldCatalog.NAME),
    OWNER_ACTOR("owner_actor_id", ContactFieldCatalog.OWNER_ACTOR);

    private final String column;
    private final FieldCode fieldCode;

    ContactSortField(String column, String fieldCode) {
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
