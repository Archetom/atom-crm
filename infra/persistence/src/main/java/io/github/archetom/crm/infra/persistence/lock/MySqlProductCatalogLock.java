package io.github.archetom.crm.infra.persistence.lock;

import io.github.archetom.crm.application.port.out.ProductCatalogLock;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ProductCatalogGuardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MySqlProductCatalogLock implements ProductCatalogLock {

    private final ProductCatalogGuardMapper mapper;

    @Override
    public void acquire(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID must not be null");
        }
        mapper.acquire(tenantId.getValue());
    }
}
