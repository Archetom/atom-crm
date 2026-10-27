package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.policy.ContractFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;

/** Server-owned contract fields that may safely participate in ordered pagination. */
public enum ContractSortField {
    UPDATED_TIME("updated_time", ContractFieldCatalog.UPDATED_TIME),
    CREATED_TIME("created_time", ContractFieldCatalog.CREATED_TIME),
    NAME("name", ContractFieldCatalog.NAME),
    OWNER_ACTOR("owner_actor_id", ContractFieldCatalog.OWNER_ACTOR);

    private final String column;
    private final FieldCode fieldCode;

    ContractSortField(String column, String fieldCode) {
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
