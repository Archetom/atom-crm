package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.CustomerCsvExportRequest;
import io.github.archetom.crm.application.export.CustomerCsvDocument;
import io.github.archetom.common.result.Result;

/** Synchronous, bounded Customer-only CSV export. */
public interface CustomerCsvExportService {
    Result<CustomerCsvDocument> export(
            AuthenticatedCaller caller, CustomerCsvExportRequest request);
}
