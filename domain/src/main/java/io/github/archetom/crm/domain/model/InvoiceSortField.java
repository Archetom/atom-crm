package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.policy.InvoiceFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;

/** Server-owned invoice fields that may safely participate in ordered pagination. */
public enum InvoiceSortField {
    UPDATED_TIME("updated_time", InvoiceFieldCatalog.UPDATED_TIME),
    CREATED_TIME("created_time", InvoiceFieldCatalog.CREATED_TIME),
    REQUESTED_DATE("requested_date", InvoiceFieldCatalog.REQUESTED_DATE),
    ISSUED_DATE("issued_date", InvoiceFieldCatalog.ISSUED_DATE),
    APPLICATION_NUMBER("application_number", InvoiceFieldCatalog.APPLICATION_NUMBER),
    INVOICE_NUMBER("invoice_number", InvoiceFieldCatalog.INVOICE_NUMBER),
    OWNER_ACTOR("owner_actor_id", InvoiceFieldCatalog.OWNER_ACTOR);

    private final String column;
    private final FieldCode fieldCode;

    InvoiceSortField(String column, String fieldCode) {
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
