package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.EnterpriseDataAdoptRequest;
import io.github.archetom.crm.api.dto.request.EnterpriseDataAdoptionPreviewRequest;
import io.github.archetom.crm.application.vo.EnterpriseDataAdoptionPreviewVO;
import io.github.archetom.crm.application.vo.EnterpriseDataAdoptionVO;
import io.github.archetom.common.result.Result;

/** Human-triggered preview and selected-fact adoption into one existing Customer. */
public interface EnterpriseDataAdoptionService {
    Result<EnterpriseDataAdoptionPreviewVO> preview(
            AuthenticatedCaller caller, EnterpriseDataAdoptionPreviewRequest request);

    Result<EnterpriseDataAdoptionVO> adopt(
            AuthenticatedCaller caller, EnterpriseDataAdoptRequest request);
}
