package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.model.WorklistItemFingerprint;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.Set;

public interface WorklistAcknowledgementRepository {
    Set<WorklistItemFingerprint> findAcknowledged(
            TenantId tenantId, MemberId memberId, Collection<WorklistItemFingerprint> items);
    void saveAll(TenantId tenantId, MemberId memberId, Collection<WorklistItemFingerprint> items);
}
