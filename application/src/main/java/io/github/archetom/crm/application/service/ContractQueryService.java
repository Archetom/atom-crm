package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContractBatchCsvExportRequest;
import io.github.archetom.crm.api.dto.request.ContractQueryRequest;
import io.github.archetom.crm.application.export.ContractCsvDocument;
import io.github.archetom.crm.application.vo.ContractVO;
import io.github.archetom.crm.application.vo.ContractCollaboratorBaselineVO;
import io.github.archetom.crm.application.vo.ContractListSummaryVO;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

public interface ContractQueryService {
    Result<Pager<ContractVO>> query(
            AuthenticatedCaller caller, ContractQueryRequest request);
    Result<ContractCsvDocument> exportSelected(
            AuthenticatedCaller caller, ContractBatchCsvExportRequest request);
    Result<ContractListSummaryVO> summary(
            AuthenticatedCaller caller, ContractQueryRequest request);
    Result<ContractVO> get(AuthenticatedCaller caller, Long contractId);
    Result<ContractCollaboratorBaselineVO> collaborators(
            AuthenticatedCaller caller, Long contractId);
}
