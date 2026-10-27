package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.CustomerDomainException;
import io.github.archetom.crm.domain.valueobject.CustomerId;

import java.util.List;

/** Ordered, bounded scalar projection for one already-authorized customer. */
public record CustomerExportRow(CustomerId customerId, List<CustomerExportCell> cells) {

    public CustomerExportRow {
        if (customerId == null || cells == null) {
            throw new CustomerDomainException("customer export row is invalid");
        }
        cells = List.copyOf(cells);
    }
}
