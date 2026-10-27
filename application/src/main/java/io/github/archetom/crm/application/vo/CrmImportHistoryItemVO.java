package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

/** Caller-owned, source-free summary of one completed CRM import. */
public record CrmImportHistoryItemVO(Long draftId, LocalDateTime completedAt, int importedRows) { }
