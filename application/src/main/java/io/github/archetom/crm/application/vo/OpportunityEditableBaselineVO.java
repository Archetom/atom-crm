package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Values safe for a caller to use as a complete opportunity replacement baseline. */
public record OpportunityEditableBaselineVO(
        Long id,
        Long version,
        boolean editable,
        String name,
        Long customerId,
        String pipelineCode,
        String stageCode,
        LocalDate expectedCloseDate,
        BigDecimal estimatedAmount,
        LocalDateTime nextContactTime,
        String description) {
}
