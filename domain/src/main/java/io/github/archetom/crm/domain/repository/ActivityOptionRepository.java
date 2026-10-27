package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.ActivityOption;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.List;

public interface ActivityOptionRepository {
    List<ActivityOption> findAll(TenantId tenantId);
    List<ActivityOption> lockAndFindAll(TenantId tenantId);
    void replaceAll(TenantId tenantId, Collection<ActivityOption> options);
}
