package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ReportDefinitionRunVO(
        Long definitionId, Long definitionVersion, String objectType,
        LocalDate from, LocalDate to, String granularity,
        List<ReportColumnVO> columns, List<ReportRowVO> rows) {

    public record ReportColumnVO(
            String key, String kind, String fieldCode, String fieldType, String label) { }
    public record ReportDimensionVO(String key, String label) { }
    public record ReportRowVO(
            LocalDate periodStart, ReportDimensionVO dimension,
            Map<String, BigDecimal> values) { }
}
