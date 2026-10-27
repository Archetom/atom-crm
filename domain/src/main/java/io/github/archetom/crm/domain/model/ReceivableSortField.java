package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.policy.ReceivableFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;

/** Server-owned receivable fields that may safely participate in ordered pagination. */
public enum ReceivableSortField {
    RECEIVED_DATE("received_date", ReceivableFieldCatalog.RECEIVED_DATE),
    CREATED_TIME("created_time", ReceivableFieldCatalog.CREATED_TIME),
    UPDATED_TIME("updated_time", ReceivableFieldCatalog.UPDATED_TIME),
    RECEIPT_NUMBER("receipt_number", ReceivableFieldCatalog.NUMBER),
    OWNER_ACTOR("owner_actor_id", ReceivableFieldCatalog.OWNER_ACTOR);

    private final String column;
    private final FieldCode fieldCode;

    ReceivableSortField(String column, String fieldCode) {
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
