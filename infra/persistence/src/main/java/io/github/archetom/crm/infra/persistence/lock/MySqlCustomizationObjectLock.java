package io.github.archetom.crm.infra.persistence.lock;

import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomizationObjectGuardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MySqlCustomizationObjectLock implements CustomizationObjectLock {

    private final CustomizationObjectGuardMapper mapper;

    @Override
    public void acquire(TenantId tenantId, BusinessObjectType objectType) {
        if (tenantId == null || objectType == null) {
            throw new IllegalArgumentException("tenant and object type must not be null");
        }
        mapper.acquire(tenantId.getValue(), objectType.name());
    }
}
