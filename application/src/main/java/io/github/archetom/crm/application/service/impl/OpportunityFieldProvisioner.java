package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Idempotently installs immutable opportunity fields during tenant bootstrap. */
@Component
@RequiredArgsConstructor
public class OpportunityFieldProvisioner {

    private final FieldDefinitionRepository fieldRepository;
    private final CustomizationObjectLock objectLock;

    public void provision(TenantId tenantId) {
        objectLock.acquire(tenantId, BusinessObjectType.OPPORTUNITY);
        for (var definition : OpportunityFieldCatalog.definitions(tenantId)) {
            if (fieldRepository.findByCode(
                    tenantId, BusinessObjectType.OPPORTUNITY,
                    definition.getCode()).isEmpty()) {
                fieldRepository.save(tenantId, definition);
            }
        }
    }
}
