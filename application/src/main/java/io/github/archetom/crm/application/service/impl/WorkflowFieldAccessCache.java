package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.EnumMap;
import java.util.Map;

/** Per-use-case cache for field access that is invariant for one member and object type. */
final class WorkflowFieldAccessCache {

    private final WorkflowFieldAccessSupport support;
    private final TenantId tenantId;
    private final MemberId memberId;
    private final EnumMap<BusinessObjectType, Map<FieldCode, EffectiveFieldAccess>> accesses =
            new EnumMap<>(BusinessObjectType.class);

    WorkflowFieldAccessCache(
            WorkflowFieldAccessSupport support, TenantId tenantId, MemberId memberId) {
        this.support = java.util.Objects.requireNonNull(support, "support");
        this.tenantId = java.util.Objects.requireNonNull(tenantId, "tenantId");
        this.memberId = java.util.Objects.requireNonNull(memberId, "memberId");
    }

    Map<FieldCode, EffectiveFieldAccess> accesses(BusinessObjectType objectType) {
        return accesses.computeIfAbsent(java.util.Objects.requireNonNull(
                objectType, "objectType"), type -> support.accesses(tenantId, memberId, type));
    }
}
