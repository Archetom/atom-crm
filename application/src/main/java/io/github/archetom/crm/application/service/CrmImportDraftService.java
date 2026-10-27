package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.export.CrmImportErrorReportDocument;
import io.github.archetom.crm.application.vo.CrmImportConfirmVO;
import io.github.archetom.crm.application.vo.CrmImportDraftVO;
import io.github.archetom.crm.application.vo.CrmImportHistoryPageVO;
import io.github.archetom.crm.application.vo.CrmImportPreviewVO;
import io.github.archetom.crm.application.vo.CrmImportTemplateVO;

/** Controlled CSV import lifecycle for CRM list objects. */
public interface CrmImportDraftService {
    Result<CrmImportDraftVO> create(AuthenticatedCaller caller, String objectType);
    Result<CrmImportTemplateVO> template(AuthenticatedCaller caller, String objectType);
    Result<CrmImportHistoryPageVO> history(AuthenticatedCaller caller, String objectType, Long page, Long size);
    Result<CrmImportPreviewVO> preview(AuthenticatedCaller caller, Long draftId);
    Result<CrmImportErrorReportDocument> errorReport(AuthenticatedCaller caller, Long draftId);
    Result<CrmImportConfirmVO> confirm(AuthenticatedCaller caller, Long draftId, Long version);
    Result<Void> withdraw(AuthenticatedCaller caller, Long draftId, Long version);
}
