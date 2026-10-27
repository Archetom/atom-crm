package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/** Applies B05 field permissions at generic workflow snapshot boundaries. */
@Component
@RequiredArgsConstructor
class WorkflowFieldAccessSupport {

    private final EffectiveFieldAccessResolver resolver;

    Map<FieldCode, EffectiveFieldAccess> accesses(
            TenantId tenantId, MemberId memberId, BusinessObjectType objectType) {
        return resolver.resolve(tenantId, memberId, objectType);
    }

    void requireWrite(
            Collection<FieldCode> fieldCodes,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        if (fieldCodes == null) {
            return;
        }
        for (FieldCode code : fieldCodes) {
            EffectiveFieldAccess access = accesses.get(code);
            if (access == null || access.accessLevel() != FieldAccessLevel.WRITE) {
                throw new io.github.archetom.crm.shared.exception.NonRetryableApplicationException(
                        ApplicationErrorCode.ACCESS_DENIED,
                        "Workflow snapshot field is not writable");
            }
        }
    }
}
