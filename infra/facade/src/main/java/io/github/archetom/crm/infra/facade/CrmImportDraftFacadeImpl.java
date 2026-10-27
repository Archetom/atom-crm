package io.github.archetom.crm.infra.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.CrmImportDraftConfirmRequest;
import io.github.archetom.crm.api.dto.request.CrmImportDraftCreateRequest;
import io.github.archetom.crm.api.dto.response.CrmImportConfirmResponse;
import io.github.archetom.crm.api.dto.response.CrmImportDraftResponse;
import io.github.archetom.crm.api.dto.response.CrmImportHistoryItemResponse;
import io.github.archetom.crm.api.dto.response.CrmImportHistoryPageResponse;
import io.github.archetom.crm.api.dto.response.CrmImportPreviewResponse;
import io.github.archetom.crm.api.dto.response.CrmImportTemplateResponse;
import io.github.archetom.crm.api.facade.CrmImportDraftFacade;
import io.github.archetom.crm.application.service.CrmImportDraftService;
import io.github.archetom.crm.application.vo.CrmImportConfirmVO;
import io.github.archetom.crm.application.vo.CrmImportDraftVO;
import io.github.archetom.crm.application.vo.CrmImportHistoryPageVO;
import io.github.archetom.crm.application.vo.CrmImportPreviewVO;
import io.github.archetom.crm.application.vo.CrmImportTemplateVO;
import io.github.archetom.crm.shared.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CrmImportDraftFacadeImpl implements CrmImportDraftFacade {
    private final CrmImportDraftService service;

    @Override
    public Result<CrmImportDraftResponse> create(AuthenticatedCaller caller, CrmImportDraftCreateRequest request) {
        return ResultUtil.map(service.create(caller, request == null ? null : request.getObjectType()),
                CrmImportDraftFacadeImpl::draft);
    }

    @Override
    public Result<CrmImportTemplateResponse> template(AuthenticatedCaller caller, String objectType) {
        return ResultUtil.map(service.template(caller, objectType), CrmImportDraftFacadeImpl::template);
    }

    @Override
    public Result<CrmImportHistoryPageResponse> history(
            AuthenticatedCaller caller, String objectType, Long page, Long size) {
        return ResultUtil.map(service.history(caller, objectType, page, size),
                CrmImportDraftFacadeImpl::history);
    }

    @Override
    public Result<CrmImportPreviewResponse> preview(AuthenticatedCaller caller, Long draftId) {
        return ResultUtil.map(service.preview(caller, draftId), CrmImportDraftFacadeImpl::preview);
    }

    @Override
    public Result<CrmImportConfirmResponse> confirm(AuthenticatedCaller caller, Long draftId,
                                                     CrmImportDraftConfirmRequest request) {
        return ResultUtil.map(service.confirm(caller, draftId, request == null ? null : request.getVersion()),
                CrmImportDraftFacadeImpl::confirm);
    }

    @Override
    public Result<Void> withdraw(AuthenticatedCaller caller, Long draftId, Long version) {
        return service.withdraw(caller, draftId, version);
    }

    private static CrmImportDraftResponse draft(CrmImportDraftVO value) {
        return new CrmImportDraftResponse().setDraftId(value.draftId()).setObjectType(value.objectType())
                .setStatus(value.status()).setVersion(value.version()).setExpiresAt(value.expiresAt())
                .setImportedRows(value.importedRows()).setAttachmentTargetType("import-draft");
    }

    private static CrmImportTemplateResponse template(CrmImportTemplateVO value) {
        return new CrmImportTemplateResponse().setObjectType(value.objectType()).setColumns(value.columns());
    }

    private static CrmImportHistoryPageResponse history(CrmImportHistoryPageVO value) {
        return new CrmImportHistoryPageResponse(value.page(), value.size(), value.total(),
                value.items().stream().map(item -> new CrmImportHistoryItemResponse(
                        item.draftId(), item.completedAt(), item.importedRows())).toList());
    }

    private static CrmImportPreviewResponse preview(CrmImportPreviewVO value) {
        return new CrmImportPreviewResponse().setDraftId(value.draftId()).setObjectType(value.objectType())
                .setStatus(value.status()).setValidRows(value.validRows()).setIssues(value.issues().stream()
                        .map(issue -> new CrmImportPreviewResponse.IssueResponse().setRowNumber(issue.rowNumber())
                                .setErrCode(issue.errCode())).toList());
    }

    private static CrmImportConfirmResponse confirm(CrmImportConfirmVO value) {
        return new CrmImportConfirmResponse().setDraftId(value.draftId()).setObjectType(value.objectType())
                .setStatus(value.status()).setImportedRows(value.importedRows()).setVersion(value.version());
    }
}
