package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.application.port.out.attachment.AttachmentByteSource;
import io.github.archetom.crm.application.vo.AttachmentUploadIntentVO;
import io.github.archetom.crm.application.vo.PublicMarketingFileDraftVO;
import io.github.archetom.crm.application.vo.PublicMarketingFileDraftWithdrawalVO;
import io.github.archetom.crm.application.vo.PublicMarketingFileUploadStatusVO;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.MarketingForm;
import io.github.archetom.crm.domain.model.FileFieldValue;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Map;

/** Dedicated anonymous public-draft boundary; none of its operations accepts a caller/member. */
public interface MarketingFileDraftService {
    Result<PublicMarketingFileDraftVO> create(String publicId);
    Result<AttachmentUploadIntentVO> createIntent(String publicId, Long draftId, String authorization,
                                                   String fieldCode,
                                                   AttachmentUploadIntentCreateRequest request);
    Result<PublicMarketingFileUploadStatusVO> upload(String publicId, Long draftId,
                                                      String uploadCapability, String fieldCode,
                                                      Long fileId, Long version,
                                                      AttachmentByteSource source);
    Result<PublicMarketingFileUploadStatusVO> uploadStatus(String publicId, Long draftId,
                                                            String authorization, String fieldCode,
                                                            Long fileId);
    Result<PublicMarketingFileDraftWithdrawalVO> withdraw(String publicId, Long draftId,
                                                           String uploadCapability);

    /** Called only inside the outer MarketingForm submission transaction after the form root lock. */
    PreparedSubmission prepareSubmission(TenantId tenantId, MarketingForm form, String authorization,
                                         Long draftId, Map<String, List<Long>> fileFields,
                                         Map<FieldCode, FieldDefinition> definitions);

    /** Publishes, binds, consumes, and persists the exact prepared candidate set atomically. */
    void consumePrepared(PreparedSubmission prepared, Long leadId);

    record PreparedSubmission(io.github.archetom.crm.domain.entity.MarketingFileDraft draft,
                              List<FileFieldValue> values,
                              List<io.github.archetom.crm.domain.repository.MarketingFileDraftRepository.StagedFile> staged) { }
}
