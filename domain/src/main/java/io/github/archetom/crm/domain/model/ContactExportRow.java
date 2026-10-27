package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ContactDomainException;
import io.github.archetom.crm.domain.valueobject.ContactId;

import java.util.List;

/** Ordered, bounded scalar projection for one already-authorized Contact. */
public record ContactExportRow(ContactId contactId, List<ContactExportCell> cells) {

    public ContactExportRow {
        if (contactId == null || cells == null) {
            throw new ContactDomainException("contact export row is invalid");
        }
        cells = List.copyOf(cells);
    }
}
