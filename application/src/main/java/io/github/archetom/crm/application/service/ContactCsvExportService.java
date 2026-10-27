package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContactCsvExportRequest;
import io.github.archetom.crm.application.export.ContactCsvDocument;

/** Synchronous, bounded Contact-only CSV export. */
public interface ContactCsvExportService {
    Result<ContactCsvDocument> export(AuthenticatedCaller caller, ContactCsvExportRequest request);
}
