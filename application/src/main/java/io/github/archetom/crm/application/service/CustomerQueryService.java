package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.CustomerDuplicateCheckRequest;
import io.github.archetom.crm.api.dto.request.CustomerQueryRequest;
import io.github.archetom.crm.application.vo.CustomerCollaboratorBaselineVO;
import io.github.archetom.crm.application.vo.CustomerHierarchyVO;
import io.github.archetom.crm.application.vo.CustomerVO;
import io.github.archetom.crm.application.vo.NearbyCustomerVO;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.math.BigDecimal;
import java.util.List;

public interface CustomerQueryService {
    Result<Pager<CustomerVO>> query(AuthenticatedCaller caller, CustomerQueryRequest request);
    Result<CustomerVO> get(AuthenticatedCaller caller, Long customerId);
    Result<CustomerCollaboratorBaselineVO> collaborators(AuthenticatedCaller caller, Long customerId);
    Result<CustomerHierarchyVO> hierarchy(AuthenticatedCaller caller, Long customerId);
    Result<Boolean> duplicateCheck(AuthenticatedCaller caller, CustomerDuplicateCheckRequest request);
    Result<Pager<NearbyCustomerVO>> nearby(AuthenticatedCaller caller,
                                           BigDecimal longitude, BigDecimal latitude,
                                           Integer radiusMeters, Integer page, Integer size);
    Result<List<CustomerVO>> summaries(AuthenticatedCaller caller, List<Long> ids);
    Result<List<String>> tags(AuthenticatedCaller caller);
}
