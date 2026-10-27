package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OaRequestPreviewRequest;
import io.github.archetom.crm.api.dto.request.OaRequestQueryRequest;
import io.github.archetom.crm.api.dto.request.OaRequestSubmitRequest;
import io.github.archetom.crm.api.dto.response.OaRequestPreviewResponse;
import io.github.archetom.crm.api.dto.response.OaRequestResponse;
import io.github.archetom.crm.api.dto.response.ResolvedApprovalStepResponse;
import io.github.archetom.crm.api.facade.OaRequestFacade;
import io.github.archetom.crm.application.service.OaRequestService;
import io.github.archetom.crm.application.vo.OaRequestVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OaRequestFacadeImpl implements OaRequestFacade {

    private final OaRequestService service;
    private final FileFieldResponseEnricher fileFieldResponseEnricher;

    @Override
    public Result<OaRequestPreviewResponse> preview(
            AuthenticatedCaller caller, OaRequestPreviewRequest request) {
        return ResultUtil.map(service.preview(caller, request), value ->
                new OaRequestPreviewResponse(value.formId(), value.formVersion(),
                        value.definitionId(), value.definitionVersion(),
                        value.autoApproved(), value.steps().stream().map(step ->
                        new ResolvedApprovalStepResponse(step.code(), step.name(),
                                step.order(), step.mode(),
                                step.assigneeActorIds())).toList()));
    }

    @Override
    public Result<OaRequestResponse> submit(
            AuthenticatedCaller caller, OaRequestSubmitRequest request) {
        return ResultUtil.map(service.submit(caller, request), value -> response(caller, value));
    }

    @Override
    public Result<Pager<OaRequestResponse>> query(
            AuthenticatedCaller caller, OaRequestQueryRequest request) {
        return ResultUtil.map(service.query(caller, request), source -> {
            Pager<OaRequestResponse> result = PageUtil.copy(source);
            result.setMeta(null);
            result.setObjectList(responses(caller, source.getObjectList()));
            return result;
        });
    }

    @Override
    public Result<OaRequestResponse> get(
            AuthenticatedCaller caller, Long requestId) {
        return ResultUtil.map(service.get(caller, requestId), value -> response(caller, value));
    }

    private OaRequestResponse response(AuthenticatedCaller caller, OaRequestVO value) {
        return response(value, fileFieldResponseEnricher.oa(caller, value.id()));
    }

    private static OaRequestResponse response(
            OaRequestVO value, java.util.Map<String, java.util.List<
                    io.github.archetom.crm.api.dto.response.AttachmentResponse>> fileFields) {
        return new OaRequestResponse(value.id(), value.formId(), value.formVersion(),
                value.approvalInstanceId(),
                value.approvalStatus(), value.values(), fileFields,
                value.version());
    }

    /** One FILE-fact read for the whole projection instead of one per row. */
    private java.util.List<OaRequestResponse> responses(
            AuthenticatedCaller caller, java.util.List<OaRequestVO> values) {
        var fileFields = fileFieldResponseEnricher.oa(
                caller, values.stream().map(OaRequestVO::id).toList());
        return values.stream()
                .map(value -> response(value, fileFields.getOrDefault(value.id(), java.util.Map.of())))
                .toList();
    }
}
