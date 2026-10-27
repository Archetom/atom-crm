package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;
import java.util.List;

public record ReportDefinitionVO(
        Long id, String name, String description, String objectType,
        String timeFieldCode, String granularity, String dimensionFieldCode,
        List<FilterConditionVO> filters, List<ReportMeasureVO> measures,
        Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {

    public record ReportMeasureVO(String type, String fieldCode, String key) { }
}
