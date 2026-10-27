package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.port.out.attachment.AttachmentByteSource;
import io.github.archetom.crm.application.vo.FileFieldUploadedFileVO;

/** Streams bytes for a staged FILE candidate; it never grants a targetless download. */
public interface FileFieldTransferService {
    Result<FileFieldUploadedFileVO> upload(AuthenticatedCaller caller, Long draftId, String fieldCode,
                                           Long fileId, Long version, AttachmentByteSource source);

    Result<FileFieldUploadedFileVO> uploadStatus(AuthenticatedCaller caller, Long draftId,
                                                  String fieldCode, Long fileId);
}
