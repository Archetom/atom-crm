package io.github.archetom.crm.infra.external;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAttribution;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataReadPolicy;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataReadPolicyResolver;
import io.github.archetom.crm.domain.model.EnterpriseDataAttributionContext;
import io.github.archetom.crm.domain.model.EnterpriseDataSourceKind;
import io.github.archetom.crm.domain.model.EnterpriseDataUsageMode;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Controlled dev/test policy catalog for the synthetic source binding. */
@Component
@Profile("(dev | test) & !prod")
@ConditionalOnProperty(
        prefix = "atom.enterprise-data.synthetic",
        name = "enabled",
        havingValue = "true")
public final class SyntheticEnterpriseDataReadPolicyResolver
        implements EnterpriseDataReadPolicyResolver {

    @Override
    public EnterpriseDataReadPolicy resolve(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant must not be null");
        }
        return new EnterpriseDataReadPolicy(true,
                EnterpriseDataSourceKind.SYNTHETIC_FIXTURE,
                EnterpriseDataUsageMode.ADOPT_SELECTED_FACTS,
                Duration.ofMinutes(5),
                attribution(EnterpriseDataAttributionContext.SEARCH_RESULT),
                attribution(EnterpriseDataAttributionContext.DETAIL));
    }

    private EnterpriseDataAttribution attribution(
            EnterpriseDataAttributionContext context) {
        return new EnterpriseDataAttribution(context,
                "Synthetic registry fixture", null,
                "Contains only fictional organization data.");
    }
}
