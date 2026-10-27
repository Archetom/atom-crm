package io.github.archetom.crm.application.vo;

/** Minimal staged FILE state suitable for client upload progress. */
public record FileFieldUploadedFileVO(Long fileId, String displayName, String mediaType,
                                      Long size, String status) {
}
