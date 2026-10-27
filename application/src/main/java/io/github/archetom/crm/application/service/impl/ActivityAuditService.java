package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.entity.Activity;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.repository.ActivityRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Emits only server-defined summaries; it never accepts caller-provided audit facts. */
@Component
@RequiredArgsConstructor
class ActivityAuditService {
    private static final String CATEGORY = "SYSTEM_AUDIT";

    private final ActivityRepository repository;

    void record(AuthenticatedCaller caller, TenantId tenantId, BusinessObjectType subjectType,
                Long subjectId, String summary) {
        if (caller == null || tenantId == null || subjectType == null || subjectId == null
                || subjectId <= 0 || summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("activity audit facts are invalid");
        }
        repository.save(tenantId, Activity.system(tenantId, subjectType, subjectId,
                new MemberId(caller.actorId()), CATEGORY, summary));
    }
}
