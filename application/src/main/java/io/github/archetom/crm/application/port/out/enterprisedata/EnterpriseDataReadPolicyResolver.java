package io.github.archetom.crm.application.port.out.enterprisedata;

import io.github.archetom.crm.domain.valueobject.TenantId;

/** Resolves the deployment-governed source policy independently of the data adapter. */
public interface EnterpriseDataReadPolicyResolver {

    EnterpriseDataReadPolicy resolve(TenantId tenantId);
}
