package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReportRankingRequest;
import io.github.archetom.crm.api.dto.request.ReportTrendRequest;
import io.github.archetom.crm.application.vo.CoreReportVO;
import io.github.archetom.crm.application.vo.ReportRankingVO;
import io.github.archetom.crm.application.vo.ReportTrendVO;
import io.github.archetom.common.result.Result;

/** Query boundary for fixed report views. */
public interface ReportQueryService {

    Result<CoreReportVO> core(AuthenticatedCaller caller);

    Result<ReportTrendVO> trend(AuthenticatedCaller caller, ReportTrendRequest request);

    Result<ReportRankingVO> ranking(AuthenticatedCaller caller, ReportRankingRequest request);
}
