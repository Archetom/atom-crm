package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record ReportDefinitionSummaryVO(
        Long id, String name, String description, String objectType,
        String timeFieldCode, String granularity, String dimensionFieldCode,
        int measureCount, int filterCount, Long version,
        LocalDateTime createdTime, LocalDateTime updatedTime) { }
