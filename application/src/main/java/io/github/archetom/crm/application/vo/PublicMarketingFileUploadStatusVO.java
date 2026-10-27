package io.github.archetom.crm.application.vo;

/** Public-safe candidate state; no scanner detail, storage information, or aggregate version. */
public record PublicMarketingFileUploadStatusVO(Long draftId, String fieldCode, Long fileId,
                                                 String displayName, String mediaType, Long size,
                                                 Long version, String status) {
}
