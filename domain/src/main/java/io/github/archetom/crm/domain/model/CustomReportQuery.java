package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.util.List;
import java.util.Map;

/** Fully resolved, bounded query passed to exactly one owning business repository. */
public record CustomReportQuery(
        ReportDateRange range,
        ReportGranularity granularity,
        ReportFieldReference timeField,
        ReportFieldReference dimensionField,
        List<ReportMeasure> measures,
        Map<FieldCode, ReportFieldReference> fields,
        List<FilterCondition> filters,
        int rowLimit) {

    public CustomReportQuery {
        if (range == null || granularity == null || timeField == null
                || measures == null || measures.isEmpty() || fields == null
                || filters == null || rowLimit < 1) {
            throw new IllegalArgumentException("custom report query is invalid");
        }
        measures = List.copyOf(measures);
        fields = Map.copyOf(fields);
        filters = List.copyOf(filters);
    }
}
