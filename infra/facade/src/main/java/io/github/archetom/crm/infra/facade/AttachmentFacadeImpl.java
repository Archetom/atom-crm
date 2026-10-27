package io.github.archetom.crm.infra.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.api.dto.response.AttachmentPageResponse;
import io.github.archetom.crm.api.dto.response.AttachmentResponse;
import io.github.archetom.crm.api.dto.response.AttachmentUploadIntentResponse;
import io.github.archetom.crm.api.dto.response.AttachmentUploadStatusResponse;
import io.github.archetom.crm.api.facade.AttachmentFacade;
import io.github.archetom.crm.application.service.AttachmentCommandService;
import io.github.archetom.crm.application.service.AttachmentQueryService;
import io.github.archetom.crm.shared.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AttachmentFacadeImpl implements AttachmentFacade {
    private final AttachmentCommandService commandService;
    private final AttachmentQueryService queryService;

    @Override public Result<AttachmentUploadIntentResponse> createIntent(AuthenticatedCaller caller,
            String targetType, Long targetId, AttachmentUploadIntentCreateRequest request) {
        return ResultUtil.map(commandService.createIntent(caller, targetType, targetId, request),
                AttachmentResponseAssembler::toResponse);
    }
    @Override public Result<AttachmentUploadStatusResponse> uploadStatus(AuthenticatedCaller caller,
            String targetType, Long targetId, Long fileId) {
        return ResultUtil.map(queryService.uploadStatus(caller, targetType, targetId, fileId),
                AttachmentResponseAssembler::toResponse);
    }
    @Override public Result<AttachmentPageResponse> query(AuthenticatedCaller caller,
            String targetType, Long targetId, Integer page, Integer size) {
        return query(caller, targetType, targetId, page, size, null);
    }
    @Override public Result<AttachmentPageResponse> query(AuthenticatedCaller caller,
            String targetType, Long targetId, Integer page, Integer size, String marketingActivityImageType) {
        return query(caller, targetType, targetId, page, size, marketingActivityImageType, null);
    }
    @Override public Result<AttachmentPageResponse> query(AuthenticatedCaller caller,
            String targetType, Long targetId, Integer page, Integer size, String marketingActivityImageType,
            String productImageType) {
        return ResultUtil.map(queryService.query(caller, targetType, targetId, page, size,
                marketingActivityImageType, productImageType), source ->
                new AttachmentPageResponse(Math.toIntExact(source.getPageNum()), Math.toIntExact(source.getPageSize()),
                        source.getTotalNum(),
                        source.getObjectList().stream().map(AttachmentResponseAssembler::toResponse).toList()));
    }
    @Override public Result<AttachmentResponse> get(AuthenticatedCaller caller, String targetType,
            Long targetId, Long fileId) {
        return ResultUtil.map(queryService.get(caller, targetType, targetId, fileId),
                AttachmentResponseAssembler::toResponse);
    }
    @Override public Result<Void> withdraw(AuthenticatedCaller caller, String targetType,
            Long targetId, Long fileId, Long version) {
        return commandService.withdraw(caller, targetType, targetId, fileId, version);
    }

}
