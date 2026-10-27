package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.CustomerBatchTagRequest;
import io.github.archetom.crm.api.dto.request.CustomerCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerCreateRequest;
import io.github.archetom.crm.api.dto.request.CustomerOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerParentUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerStateUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerTagsUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerUpdateRequest;
import io.github.archetom.crm.application.vo.CustomerVO;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface CustomerCommandService {
    Result<CustomerVO> create(AuthenticatedCaller caller, CustomerCreateRequest request);
    Result<CustomerVO> update(AuthenticatedCaller caller, Long customerId, CustomerUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long customerId, Long version);
    Result<CustomerVO> updateState(AuthenticatedCaller caller, Long customerId,
                                   CustomerStateUpdateRequest request);
    Result<CustomerVO> updateOwner(AuthenticatedCaller caller, Long customerId,
                                   CustomerOwnerUpdateRequest request);
    Result<CustomerVO> replaceTags(AuthenticatedCaller caller, Long customerId,
                                   CustomerTagsUpdateRequest request);
    Result<List<CustomerVO>> changeTags(AuthenticatedCaller caller, CustomerBatchTagRequest request);
    Result<CustomerVO> replaceCollaborators(AuthenticatedCaller caller, Long customerId,
                                            CustomerCollaboratorsUpdateRequest request);
    Result<Void> exitCollaborator(AuthenticatedCaller caller, Long customerId, Long version);
    Result<CustomerVO> updateParent(AuthenticatedCaller caller, Long customerId,
                                    CustomerParentUpdateRequest request);
    Result<Void> setStarred(AuthenticatedCaller caller, Long customerId,
                            CustomerStarUpdateRequest request);
}
