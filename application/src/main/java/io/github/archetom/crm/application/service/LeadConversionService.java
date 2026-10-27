package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.LeadConvertRequest;
import io.github.archetom.crm.application.vo.LeadConversionVO;
import io.github.archetom.common.result.Result;

/** Coordinates the atomic customer/contact projection of one lead. */
public interface LeadConversionService {

    Result<LeadConversionVO> convert(
            AuthenticatedCaller caller, Long leadId, LeadConvertRequest request);
}
