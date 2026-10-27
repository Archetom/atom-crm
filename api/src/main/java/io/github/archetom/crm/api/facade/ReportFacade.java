package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReportRankingRequest;
import io.github.archetom.crm.api.dto.request.ReportTrendRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionCreateRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionQueryRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionRunRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionUpdateRequest;
import io.github.archetom.crm.api.dto.response.CoreReportResponse;
import io.github.archetom.crm.api.dto.response.ReportRankingResponse;
import io.github.archetom.crm.api.dto.response.ReportTrendResponse;
import io.github.archetom.crm.api.dto.response.ReportCatalogResponse;
import io.github.archetom.crm.api.dto.response.ReportDefinitionResponse;
import io.github.archetom.crm.api.dto.response.ReportDefinitionRunResponse;
import io.github.archetom.crm.api.dto.response.ReportDefinitionSummaryResponse;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

public interface ReportFacade {

    Result<CoreReportResponse> core(AuthenticatedCaller caller);

    Result<ReportTrendResponse> trend(AuthenticatedCaller caller, ReportTrendRequest request);

    Result<ReportRankingResponse> ranking(
            AuthenticatedCaller caller, ReportRankingRequest request);

    Result<ReportCatalogResponse> catalog(AuthenticatedCaller caller, String objectType);
    Result<Pager<ReportDefinitionSummaryResponse>> queryDefinitions(
            AuthenticatedCaller caller, ReportDefinitionQueryRequest request);
    Result<ReportDefinitionResponse> getDefinition(AuthenticatedCaller caller, Long definitionId);
    Result<ReportDefinitionResponse> createDefinition(
            AuthenticatedCaller caller, ReportDefinitionCreateRequest request);
    Result<ReportDefinitionResponse> updateDefinition(
            AuthenticatedCaller caller, Long definitionId, ReportDefinitionUpdateRequest request);
    Result<Void> deleteDefinition(
            AuthenticatedCaller caller, Long definitionId, Long version);
    Result<ReportDefinitionRunResponse> runDefinition(
            AuthenticatedCaller caller, Long definitionId, ReportDefinitionRunRequest request);
}
