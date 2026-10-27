package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.application.vo.AttachmentUploadIntentVO;

public interface AttachmentCommandService {
    Result<AttachmentUploadIntentVO> createIntent(AuthenticatedCaller caller, String targetType,
                                                   Long targetId,
                                                   AttachmentUploadIntentCreateRequest request);
    Result<Void> withdraw(AuthenticatedCaller caller, String targetType, Long targetId,
                          Long fileId, Long version);
}
