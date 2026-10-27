package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.NavigationPreference;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Optional;

/** Tenant-scoped persistence port for member navigation preferences. */
public interface NavigationPreferenceRepository {

    NavigationPreference save(TenantId tenantId, NavigationPreference preference);

    Optional<NavigationPreference> findByMember(TenantId tenantId, MemberId memberId);
}
