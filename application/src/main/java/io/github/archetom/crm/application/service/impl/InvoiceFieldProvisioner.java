package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.policy.InvoiceFieldCatalog;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvoiceFieldProvisioner {
    private final FieldDefinitionRepository fieldRepository;
    private final CustomizationObjectLock objectLock;

    public void provision(TenantId tenantId) {
        objectLock.acquire(tenantId, BusinessObjectType.INVOICE);
        for (var definition : InvoiceFieldCatalog.definitions(tenantId)) {
            if (fieldRepository.findByCode(tenantId, BusinessObjectType.INVOICE,
                    definition.getCode()).isEmpty()) {
                fieldRepository.save(tenantId, definition);
            }
        }
    }
}
