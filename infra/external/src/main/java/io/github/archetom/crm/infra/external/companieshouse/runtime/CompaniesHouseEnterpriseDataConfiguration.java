package io.github.archetom.crm.infra.external.companieshouse.runtime;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataReadPolicyResolver;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Explicit Companies House composition. It remains NOT_READY until independent live evidence is added. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "atom.enterprise-data.companies-house", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CompaniesHouseEnterpriseDataProperties.class)
public class CompaniesHouseEnterpriseDataConfiguration {
    @Bean
    @ConditionalOnMissingBean(EnterpriseDataSource.class)
    EnterpriseDataSource companiesHouseEnterpriseDataSource(CompaniesHouseEnterpriseDataProperties properties) {
        return new CompaniesHouseEnterpriseDataSource(properties);
    }

    @Bean
    @ConditionalOnMissingBean(EnterpriseDataReadPolicyResolver.class)
    EnterpriseDataReadPolicyResolver companiesHouseReadPolicyResolver(
            CompaniesHouseEnterpriseDataProperties properties) {
        return tenantId -> EnterpriseDataReadPolicyResolverPolicy.resolve(properties, tenantId);
    }

    private static final class EnterpriseDataReadPolicyResolverPolicy {
        private static io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataReadPolicy resolve(
                CompaniesHouseEnterpriseDataProperties properties,
                io.github.archetom.crm.domain.valueobject.TenantId tenantId) {
            // The runtime adapter independently refuses all production calls until live-canary
            // evidence is introduced; the resolver mirrors that state for query readiness.
            return io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataReadPolicy.unavailable();
        }
    }
}
