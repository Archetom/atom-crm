package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.model.WorklistSource;
import io.github.archetom.crm.domain.model.WorklistSourceRecord;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.LocalDateTime;
import java.util.List;

public interface WorklistSourceRepository {
    List<WorklistSourceRecord> findDue(
            TenantId tenantId, MemberId memberId, WorklistSource source,
            LocalDateTime fromInclusive, LocalDateTime untilExclusive,
            LocalDateTime afterTime, Long afterId, int limit);
}
