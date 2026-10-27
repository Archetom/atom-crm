package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;

/** Server-owned opportunity fields that may safely participate in ordered pagination. */
public enum OpportunitySortField {
    UPDATED_TIME("updated_time", OpportunityFieldCatalog.UPDATED_TIME),
    CREATED_TIME("created_time", OpportunityFieldCatalog.CREATED_TIME),
    NAME("name", OpportunityFieldCatalog.NAME),
    OWNER_ACTOR("owner_actor_id", OpportunityFieldCatalog.OWNER_ACTOR);

    private final String column;
    private final FieldCode fieldCode;

    OpportunitySortField(String column, String fieldCode) {
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
