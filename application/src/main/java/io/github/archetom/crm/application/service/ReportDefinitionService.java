package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReportDefinitionCreateRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionQueryRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionRunRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionUpdateRequest;
import io.github.archetom.crm.application.vo.ReportCatalogVO;
import io.github.archetom.crm.application.vo.ReportDefinitionRunVO;
import io.github.archetom.crm.application.vo.ReportDefinitionSummaryVO;
import io.github.archetom.crm.application.vo.ReportDefinitionVO;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

public interface ReportDefinitionService {
    Result<ReportCatalogVO> catalog(AuthenticatedCaller caller, String objectType);
    Result<Pager<ReportDefinitionSummaryVO>> query(
            AuthenticatedCaller caller, ReportDefinitionQueryRequest request);
    Result<ReportDefinitionVO> get(AuthenticatedCaller caller, Long definitionId);
    Result<ReportDefinitionVO> create(
            AuthenticatedCaller caller, ReportDefinitionCreateRequest request);
    Result<ReportDefinitionVO> update(
            AuthenticatedCaller caller, Long definitionId, ReportDefinitionUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long definitionId, Long version);
    Result<ReportDefinitionRunVO> run(
            AuthenticatedCaller caller, Long definitionId, ReportDefinitionRunRequest request);
}
