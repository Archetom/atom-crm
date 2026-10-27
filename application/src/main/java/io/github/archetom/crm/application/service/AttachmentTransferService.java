package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.port.out.attachment.AttachmentByteSource;
import io.github.archetom.crm.application.port.out.attachment.AttachmentReadHandle;

public interface AttachmentTransferService {
    Result<String> upload(AuthenticatedCaller caller, String targetType, Long targetId,
                          Long fileId, Long version, AttachmentByteSource source);
    Result<Download> openDownload(AuthenticatedCaller caller, String targetType, Long targetId,
                                  Long fileId, String rangeHeader);
    Result<Download> openOaFileFieldDownload(AuthenticatedCaller caller, Long requestId,
                                             String fieldCode, Long fileId, String rangeHeader);
    Result<Download> openCrmFileFieldDownload(AuthenticatedCaller caller, String targetType, Long targetId,
                                              String fieldCode, Long fileId, String rangeHeader);
    Result<Download> openApprovalFileFieldDownload(AuthenticatedCaller caller, Long instanceId,
                                                   String fieldCode, Long fileId, String rangeHeader);
    Result<Download> openStageFileFieldDownload(AuthenticatedCaller caller, Long recordId,
                                                String stageCode, String fieldCode, Long fileId,
                                                String rangeHeader);

    record Download(AttachmentReadHandle handle, String displayName, String mediaType,
                    long totalBytes, long offset, long length, boolean partial,
                    boolean singleRangeReady) { }
}
