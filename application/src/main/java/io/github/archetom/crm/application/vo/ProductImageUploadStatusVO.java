package io.github.archetom.crm.application.vo;

/** Safe staged Product image status; scanner reasons and storage details stay internal. */
public record ProductImageUploadStatusVO(Long draftId, String imageType, Long fileId,
                                         String displayName, String mediaType, Long size,
                                         String status) { }
