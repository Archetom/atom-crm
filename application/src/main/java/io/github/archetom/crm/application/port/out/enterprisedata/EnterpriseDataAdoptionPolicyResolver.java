package io.github.archetom.crm.application.port.out.enterprisedata;

import io.github.archetom.crm.domain.valueobject.TenantId;

/** Resolves the stricter adoption policy independently from query/display policy. */
public interface EnterpriseDataAdoptionPolicyResolver {
    EnterpriseDataAdoptionPolicy resolve(TenantId tenantId);
}
