package io.github.archetom.crm.infra.external;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAdoptionPolicy;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAdoptionPolicyResolver;
import io.github.archetom.crm.domain.valueobject.TenantId;

/** Default deployment policy: source display never implies Customer adoption permission. */
public final class DisabledEnterpriseDataAdoptionPolicyResolver
        implements EnterpriseDataAdoptionPolicyResolver {
    @Override
    public EnterpriseDataAdoptionPolicy resolve(TenantId tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("tenant must not be null");
        return EnterpriseDataAdoptionPolicy.unavailable();
    }
}
