package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;

/** Server-owned customer fields that may safely participate in ordered pagination. */
public enum CustomerSortField {
    UPDATED_TIME("updated_time", CustomerFieldCatalog.UPDATED_TIME),
    CREATED_TIME("created_time", CustomerFieldCatalog.CREATED_TIME),
    NAME("name", CustomerFieldCatalog.NAME),
    OWNER_ACTOR("owner_actor_id", CustomerFieldCatalog.OWNER_ACTOR);

    private final String column;
    private final FieldCode fieldCode;

    CustomerSortField(String column, String fieldCode) {
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
