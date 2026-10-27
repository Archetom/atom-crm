package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record FileFieldDraftVO(Long id, String objectType, Long formId,
                               Long formVersion, Long version, LocalDateTime expiresAt) { }
