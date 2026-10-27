package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record CrmImportDraftVO(Long draftId, String objectType, String status, Long version,
                               LocalDateTime expiresAt, int importedRows) { }
