package io.github.archetom.crm.application.vo;

import java.util.List;

/** Bounded page of current-member CRM import summaries. */
public record CrmImportHistoryPageVO(Long page, Long size, Long total,
                                     List<CrmImportHistoryItemVO> items) { }
