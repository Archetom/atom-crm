package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.CustomizationDomainException;
import io.github.archetom.crm.domain.valueobject.FieldCode;

/** A server-validated aggregate; it never contains an expression or a storage identifier. */
public record ReportMeasure(ReportMeasureType type, FieldCode fieldCode) {

    public ReportMeasure {
        if (type == null || type == ReportMeasureType.COUNT && fieldCode != null
                || type == ReportMeasureType.SUM && fieldCode == null) {
            throw new CustomizationDomainException("report measure is invalid");
        }
    }

    public static ReportMeasure count() {
        return new ReportMeasure(ReportMeasureType.COUNT, null);
    }

    public static ReportMeasure sum(FieldCode fieldCode) {
        return new ReportMeasure(ReportMeasureType.SUM, fieldCode);
    }

    public String key() {
        return type == ReportMeasureType.COUNT
                ? "COUNT" : "SUM:" + fieldCode.getValue();
    }
}
