package io.github.archetom.crm.infra.persistence.lock;

import io.github.archetom.crm.application.port.out.OpportunityPipelineCatalogLock;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OpportunityPipelineCatalogGuardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MySqlOpportunityPipelineCatalogLock implements OpportunityPipelineCatalogLock {

    private final OpportunityPipelineCatalogGuardMapper mapper;

    @Override
    public void acquire(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID is required");
        }
        mapper.acquire(tenantId.getValue());
    }
}
