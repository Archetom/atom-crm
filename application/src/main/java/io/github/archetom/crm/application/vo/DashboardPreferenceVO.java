package io.github.archetom.crm.application.vo;

import java.time.LocalDate;
import java.util.List;

/** Current caller's typed dashboard defaults. */
public record DashboardPreferenceVO(
        String timeRange,
        LocalDate customStartDate,
        LocalDate customEndDate,
        String dataScope,
        List<String> hiddenModules,
        Long version) {
}
