package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.LeadDomainException;
import io.github.archetom.crm.domain.valueobject.LeadId;

import java.util.List;

/** Ordered, bounded scalar projection for one already-authorized Lead. */
public record LeadExportRow(LeadId leadId, List<LeadExportCell> cells) {

    public LeadExportRow {
        if (leadId == null || cells == null) {
            throw new LeadDomainException("lead export row is invalid");
        }
        cells = List.copyOf(cells);
    }
}
