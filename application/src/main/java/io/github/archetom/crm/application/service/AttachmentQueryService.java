package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.vo.AttachmentUploadStatusVO;
import io.github.archetom.crm.application.vo.AttachmentVO;

public interface AttachmentQueryService {
    Result<AttachmentUploadStatusVO> uploadStatus(AuthenticatedCaller caller, String targetType,
                                                   Long targetId, Long fileId);
    Result<Pager<AttachmentVO>> query(AuthenticatedCaller caller, String targetType, Long targetId,
                                      Integer page, Integer size);
    Result<Pager<AttachmentVO>> query(AuthenticatedCaller caller, String targetType, Long targetId,
                                      Integer page, Integer size,
                                      String marketingActivityImageType);
    Result<Pager<AttachmentVO>> query(AuthenticatedCaller caller, String targetType, Long targetId,
                                      Integer page, Integer size, String marketingActivityImageType,
                                      String productImageType);
    Result<AttachmentVO> get(AuthenticatedCaller caller, String targetType, Long targetId,
                             Long fileId);
}
