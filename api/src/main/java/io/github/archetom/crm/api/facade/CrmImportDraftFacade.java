package io.github.archetom.crm.api.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.CrmImportDraftConfirmRequest;
import io.github.archetom.crm.api.dto.request.CrmImportDraftCreateRequest;
import io.github.archetom.crm.api.dto.response.CrmImportConfirmResponse;
import io.github.archetom.crm.api.dto.response.CrmImportDraftResponse;
import io.github.archetom.crm.api.dto.response.CrmImportHistoryPageResponse;
import io.github.archetom.crm.api.dto.response.CrmImportPreviewResponse;
import io.github.archetom.crm.api.dto.response.CrmImportTemplateResponse;

public interface CrmImportDraftFacade {
    Result<CrmImportDraftResponse> create(AuthenticatedCaller caller, CrmImportDraftCreateRequest request);
    Result<CrmImportTemplateResponse> template(AuthenticatedCaller caller, String objectType);
    Result<CrmImportHistoryPageResponse> history(AuthenticatedCaller caller, String objectType, Long page, Long size);
    Result<CrmImportPreviewResponse> preview(AuthenticatedCaller caller, Long draftId);
    Result<CrmImportConfirmResponse> confirm(AuthenticatedCaller caller, Long draftId,
                                             CrmImportDraftConfirmRequest request);
    Result<Void> withdraw(AuthenticatedCaller caller, Long draftId, Long version);
}
