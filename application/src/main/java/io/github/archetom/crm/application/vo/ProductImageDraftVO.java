package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

/** Minimal authenticated capability for one Product create media draft. */
public record ProductImageDraftVO(Long draftId, Long version, LocalDateTime expiresAt) { }
