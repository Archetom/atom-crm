package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.policy.ProductFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;

/** Server-owned product fields that may safely participate in ordered pagination. */
public enum ProductSortField {
    UPDATED_TIME("updated_time", ProductFieldCatalog.UPDATED_TIME),
    CREATED_TIME("created_time", ProductFieldCatalog.CREATED_TIME),
    NAME("name", ProductFieldCatalog.NAME),
    OWNER_ACTOR("owner_actor_id", ProductFieldCatalog.OWNER_ACTOR);

    private final String column;
    private final FieldCode fieldCode;

    ProductSortField(String column, String fieldCode) {
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
