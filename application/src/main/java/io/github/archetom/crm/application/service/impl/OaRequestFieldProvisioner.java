package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.policy.OaRequestFieldCatalog;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OaRequestFieldProvisioner {
    private final FieldDefinitionRepository fieldRepository;
    private final CustomizationObjectLock objectLock;

    public void provision(TenantId tenantId) {
        objectLock.acquire(tenantId, BusinessObjectType.OA_REQUEST);
        for (var definition : OaRequestFieldCatalog.definitions(tenantId)) {
            if (fieldRepository.findByCode(tenantId, BusinessObjectType.OA_REQUEST,
                    definition.getCode()).isEmpty()) {
                fieldRepository.save(tenantId, definition);
            }
        }
    }
}
