package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.domain.entity.ApprovalDefinition;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.OaFormDefinition;
import io.github.archetom.crm.domain.entity.OaFormField;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;

import java.util.Map;

/** Shared server-side eligibility rule for both OA form discovery and request submission. */
final class OaFormAvailability {

    private OaFormAvailability() {
    }

    static boolean isAvailable(
            OaFormDefinition form, MemberId memberId, DepartmentId departmentId,
            ApprovalDefinition approval, Map<FieldCode, FieldDefinition> definitions,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        if (!form.isActive() || approval == null || !approval.isActive()
                || approval.getObjectType() != BusinessObjectType.OA_REQUEST
                || !approval.isApplicantAllowed(memberId, departmentId)) {
            return false;
        }
        for (OaFormField field : form.getFields()) {
            FieldDefinition definition = definitions.get(field.fieldCode());
            EffectiveFieldAccess access = accesses.get(field.fieldCode());
            if (definition == null || !definition.isActive() || access == null
                    || !access.accessLevel().covers(FieldAccessLevel.WRITE)) {
                return false;
            }
        }
        return true;
    }
}
