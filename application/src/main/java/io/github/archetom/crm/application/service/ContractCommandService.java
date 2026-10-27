package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContractBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchCollaboratorAddRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchCollaboratorRemoveRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractCreateRequest;
import io.github.archetom.crm.api.dto.request.ContractOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractUpdateRequest;
import io.github.archetom.crm.application.vo.ContractVO;
import io.github.archetom.common.result.Result;

public interface ContractCommandService {
    Result<ContractVO> create(AuthenticatedCaller caller, ContractCreateRequest request);
    Result<ContractVO> update(
            AuthenticatedCaller caller, Long contractId, ContractUpdateRequest request);
    Result<ContractVO> updateOwner(
            AuthenticatedCaller caller, Long contractId, ContractOwnerUpdateRequest request);
    Result<Void> updateOwners(
            AuthenticatedCaller caller, ContractBatchOwnerUpdateRequest request);
    Result<Void> addCollaborators(
            AuthenticatedCaller caller, ContractBatchCollaboratorAddRequest request);
    Result<Void> removeCollaborators(
            AuthenticatedCaller caller, ContractBatchCollaboratorRemoveRequest request);
    Result<ContractVO> replaceCollaborators(
            AuthenticatedCaller caller, Long contractId,
            ContractCollaboratorsUpdateRequest request);
    Result<ContractVO> exitCollaborator(
            AuthenticatedCaller caller, Long contractId, Long version);
    Result<ContractVO> voidContract(
            AuthenticatedCaller caller, Long contractId, Long version);
    Result<Void> delete(AuthenticatedCaller caller, Long contractId, Long version);
    Result<Void> deleteBatch(AuthenticatedCaller caller, ContractBatchDeleteRequest request);
}
