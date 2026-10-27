package io.github.archetom.crm.infra.persistence.lock;

import io.github.archetom.crm.application.port.out.CustomerStructureLock;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomerGuardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MySqlCustomerStructureLock implements CustomerStructureLock {

    private final CustomerGuardMapper mapper;

    @Override
    public void acquire(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID must not be null");
        }
        mapper.acquire(tenantId.getValue());
    }
}
