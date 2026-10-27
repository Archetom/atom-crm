package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContactDuplicateCheckRequest;
import io.github.archetom.crm.api.dto.request.ContactQueryRequest;
import io.github.archetom.crm.application.vo.ContactCollaboratorBaselineVO;
import io.github.archetom.crm.application.vo.ContactHierarchyVO;
import io.github.archetom.crm.application.vo.ContactVO;
import io.github.archetom.crm.application.vo.NearbyContactVO;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.math.BigDecimal;
import java.util.List;

public interface ContactQueryService {
    Result<Pager<ContactVO>> query(AuthenticatedCaller caller, ContactQueryRequest request);
    Result<ContactVO> get(AuthenticatedCaller caller, Long contactId);
    Result<ContactCollaboratorBaselineVO> collaborators(AuthenticatedCaller caller, Long contactId);
    Result<ContactHierarchyVO> hierarchy(AuthenticatedCaller caller, Long contactId);
    Result<Boolean> duplicateCheck(AuthenticatedCaller caller, ContactDuplicateCheckRequest request);
    Result<Pager<NearbyContactVO>> nearby(AuthenticatedCaller caller,
                                           BigDecimal longitude, BigDecimal latitude,
                                           Integer radiusMeters, Integer page, Integer size);
    Result<List<ContactVO>> summaries(AuthenticatedCaller caller, List<Long> ids);
    Result<List<String>> tags(AuthenticatedCaller caller);
}
