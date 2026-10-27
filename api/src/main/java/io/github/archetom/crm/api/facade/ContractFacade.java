package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContractBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchCollaboratorAddRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchCollaboratorRemoveRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractCreateRequest;
import io.github.archetom.crm.api.dto.request.ContractOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractProductsUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractQueryRequest;
import io.github.archetom.crm.api.dto.request.ContractUpdateRequest;
import io.github.archetom.crm.api.dto.response.ContractResponse;
import io.github.archetom.crm.api.dto.response.ContractCollaboratorBaselineResponse;
import io.github.archetom.crm.api.dto.response.ContractListSummaryResponse;
import io.github.archetom.crm.api.dto.response.ContractProductsResponse;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

public interface ContractFacade {
    Result<Pager<ContractResponse>> query(
            AuthenticatedCaller caller, ContractQueryRequest request);
    Result<ContractListSummaryResponse> summary(
            AuthenticatedCaller caller, ContractQueryRequest request);
    Result<ContractResponse> get(AuthenticatedCaller caller, Long contractId);
    Result<ContractCollaboratorBaselineResponse> collaborators(
            AuthenticatedCaller caller, Long contractId);
    Result<ContractProductsResponse> products(
            AuthenticatedCaller caller, Long contractId);
    Result<ContractResponse> create(
            AuthenticatedCaller caller, ContractCreateRequest request);
    Result<ContractResponse> update(
            AuthenticatedCaller caller, Long contractId, ContractUpdateRequest request);
    Result<ContractProductsResponse> replaceProducts(
            AuthenticatedCaller caller, Long contractId,
            ContractProductsUpdateRequest request);
    Result<ContractResponse> updateOwner(
            AuthenticatedCaller caller, Long contractId,
            ContractOwnerUpdateRequest request);
    Result<Void> updateOwners(
            AuthenticatedCaller caller, ContractBatchOwnerUpdateRequest request);
    Result<Void> addCollaborators(
            AuthenticatedCaller caller, ContractBatchCollaboratorAddRequest request);
    Result<Void> removeCollaborators(
            AuthenticatedCaller caller, ContractBatchCollaboratorRemoveRequest request);
    Result<ContractResponse> replaceCollaborators(
            AuthenticatedCaller caller, Long contractId,
            ContractCollaboratorsUpdateRequest request);
    Result<ContractResponse> exitCollaborator(
            AuthenticatedCaller caller, Long contractId, Long version);
    Result<ContractResponse> voidContract(
            AuthenticatedCaller caller, Long contractId, Long version);
    Result<Void> delete(AuthenticatedCaller caller, Long contractId, Long version);
    Result<Void> deleteBatch(AuthenticatedCaller caller, ContractBatchDeleteRequest request);
}
