package io.github.archetom.crm.application.security;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.Map;

/** Downstream use cases call this only after their own authority and data-scope checks. */
public interface EffectiveFieldAccessResolver {
    Map<FieldCode, EffectiveFieldAccess> resolve(
            TenantId tenantId, MemberId memberId, BusinessObjectType objectType);

    Map<FieldCode, EffectiveFieldAccess> resolve(
            TenantId tenantId, MemberId memberId, BusinessObjectType objectType,
            Collection<FieldDefinition> fields);
}
