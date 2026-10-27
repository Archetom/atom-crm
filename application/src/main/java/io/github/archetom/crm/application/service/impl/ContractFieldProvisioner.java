package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.policy.ContractFieldCatalog;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContractFieldProvisioner {
    private final FieldDefinitionRepository fieldRepository;
    private final CustomizationObjectLock objectLock;

    public void provision(TenantId tenantId) {
        objectLock.acquire(tenantId, BusinessObjectType.CONTRACT);
        for (var definition : ContractFieldCatalog.definitions(tenantId)) {
            if (fieldRepository.findByCode(tenantId, BusinessObjectType.CONTRACT,
                    definition.getCode()).isEmpty()) {
                fieldRepository.save(tenantId, definition);
            }
        }
    }
}
