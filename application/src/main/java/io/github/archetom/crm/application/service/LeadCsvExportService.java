package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.LeadCsvExportRequest;
import io.github.archetom.crm.application.export.LeadCsvDocument;

/** Produces one bounded CSV document for the caller's currently visible leads. */
public interface LeadCsvExportService {
    Result<LeadCsvDocument> export(AuthenticatedCaller caller, LeadCsvExportRequest request);
}
