package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReceivablePlanCsvExportRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanQueryRequest;
import io.github.archetom.crm.application.export.ReceivablePlanCsvDocument;
import io.github.archetom.crm.application.vo.ReceivablePlanVO;
import io.github.archetom.crm.application.vo.ReceivablePlanListSummaryVO;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface ReceivablePlanQueryService {
    Result<Pager<ReceivablePlanVO>> query(
            AuthenticatedCaller caller, ReceivablePlanQueryRequest request);
    Result<ReceivablePlanCsvDocument> export(
            AuthenticatedCaller caller, ReceivablePlanCsvExportRequest request);
    Result<ReceivablePlanListSummaryVO> summary(
            AuthenticatedCaller caller, ReceivablePlanQueryRequest request);
    Result<ReceivablePlanVO> get(AuthenticatedCaller caller, Long planId);
    Result<List<ReceivablePlanVO>> available(
            AuthenticatedCaller caller, Long contractId);
}
