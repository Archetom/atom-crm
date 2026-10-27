package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Application projection for a reviewed member monthly performance target. */
public record PerformanceTargetVO(
        long id,
        long memberId,
        String metric,
        int targetYear,
        int targetMonth,
        BigDecimal amount,
        long version,
        LocalDateTime createdTime,
        LocalDateTime updatedTime) {
}
