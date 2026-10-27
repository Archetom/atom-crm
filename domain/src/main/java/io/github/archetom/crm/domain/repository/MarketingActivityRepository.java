package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.MarketingActivity;
import io.github.archetom.crm.domain.model.MarketingActivityQuery;
import io.github.archetom.crm.domain.valueobject.MarketingActivityId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Optional;

public interface MarketingActivityRepository {
    MarketingActivity save(TenantId tenantId, MarketingActivity activity);

    Optional<MarketingActivity> findById(TenantId tenantId, MarketingActivityId activityId);

    Optional<MarketingActivity> findByIdForUpdate(TenantId tenantId, MarketingActivityId activityId);

    PageResult<MarketingActivity> findPage(TenantId tenantId, MarketingActivityQuery query);
}
