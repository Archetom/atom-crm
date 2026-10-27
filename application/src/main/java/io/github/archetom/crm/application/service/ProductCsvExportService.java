package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ProductCsvExportRequest;
import io.github.archetom.crm.application.export.ProductCsvDocument;

public interface ProductCsvExportService {

    Result<ProductCsvDocument> export(AuthenticatedCaller caller, ProductCsvExportRequest request);
}
