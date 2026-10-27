package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record AttachmentVO(Long fileId, String displayName, String mediaType, Long actualBytes,
                           Long uploaderActorId, LocalDateTime createdTime,
                           LocalDateTime availableTime, Long version) { }
