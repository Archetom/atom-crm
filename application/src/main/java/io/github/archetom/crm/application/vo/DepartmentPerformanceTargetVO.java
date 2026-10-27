package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Application projection for a reviewed department performance target. */
public record DepartmentPerformanceTargetVO(
        long id,
        long departmentId,
        String metric,
        int targetYear,
        String period,
        int periodNumber,
        BigDecimal amount,
        long version,
        LocalDateTime createdTime,
        LocalDateTime updatedTime) {
}
