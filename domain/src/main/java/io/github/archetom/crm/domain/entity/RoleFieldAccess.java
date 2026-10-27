package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.CustomizationDomainException;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FieldDefinitionId;

/** One role's explicit access to one active field. Absence means hidden. */
public record RoleFieldAccess(FieldDefinitionId fieldId, FieldCode fieldCode,
                              FieldAccessLevel accessLevel, FieldMaskType maskType) {

    public RoleFieldAccess {
        if (fieldId == null || fieldCode == null || accessLevel == null || maskType == null) {
            throw new CustomizationDomainException("role field access is incomplete");
        }
        if (accessLevel == FieldAccessLevel.HIDDEN) {
            throw new CustomizationDomainException("hidden field access must be represented by absence");
        }
        if (accessLevel == FieldAccessLevel.WRITE && maskType != FieldMaskType.NONE) {
            throw new CustomizationDomainException("writable fields cannot be masked");
        }
    }
}
