package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.policy.ReturnVisitFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;

/** Server-owned return-visit fields that may safely participate in ordered pagination. */
public enum ReturnVisitSortField {
    VISIT_TIME("visit_time", ReturnVisitFieldCatalog.VISIT_TIME),
    UPDATED_TIME("updated_time", ReturnVisitFieldCatalog.UPDATED_TIME),
    CREATED_TIME("created_time", ReturnVisitFieldCatalog.CREATED_TIME),
    VISIT_NUMBER("visit_number", ReturnVisitFieldCatalog.NUMBER),
    OWNER_ACTOR("owner_actor_id", ReturnVisitFieldCatalog.OWNER_ACTOR);

    private final String column;
    private final FieldCode fieldCode;

    ReturnVisitSortField(String column, String fieldCode) {
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
