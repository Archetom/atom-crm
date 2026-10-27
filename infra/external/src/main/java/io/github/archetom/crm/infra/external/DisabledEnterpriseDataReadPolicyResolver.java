package io.github.archetom.crm.infra.external;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataReadPolicy;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataReadPolicyResolver;
import io.github.archetom.crm.domain.valueobject.TenantId;

/** Fail-closed deployment policy used when no governed source directory is installed. */
public final class DisabledEnterpriseDataReadPolicyResolver
        implements EnterpriseDataReadPolicyResolver {

    @Override
    public EnterpriseDataReadPolicy resolve(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant must not be null");
        }
        return EnterpriseDataReadPolicy.unavailable();
    }
}
