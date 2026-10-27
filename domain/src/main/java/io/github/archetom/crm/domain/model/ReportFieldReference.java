package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.valueobject.FieldCode;

/** Resolved metadata handed to a source repository; custom fields are addressed by numeric ID. */
public record ReportFieldReference(
        FieldCode code, CustomFieldType type, boolean builtin, Long definitionId) {

    public ReportFieldReference {
        if (code == null || type == null || definitionId == null || definitionId <= 0) {
            throw new IllegalArgumentException("report field reference is invalid");
        }
    }
}
