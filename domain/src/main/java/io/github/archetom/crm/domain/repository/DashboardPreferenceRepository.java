package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.DashboardPreference;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Optional;

/** Tenant-scoped persistence port for current-member dashboard preferences. */
public interface DashboardPreferenceRepository {

    DashboardPreference save(TenantId tenantId, DashboardPreference preference);

    Optional<DashboardPreference> findByMember(TenantId tenantId, MemberId memberId);
}
