package io.github.archetom.crm.infra.external;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSource;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataReadPolicyResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Supplies the fail-closed adapter unless a deployment has installed one explicit source. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "atom.enterprise-data.gleif", name = "enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnProperty(prefix = "atom.enterprise-data.companies-house", name = "enabled", havingValue = "false", matchIfMissing = true)
public class EnterpriseDataAdapterConfiguration {

    @Bean
    @ConditionalOnMissingBean(EnterpriseDataSource.class)
    EnterpriseDataSource disabledEnterpriseDataSource() {
        return new DisabledEnterpriseDataSource();
    }

    @Bean
    @ConditionalOnMissingBean(EnterpriseDataReadPolicyResolver.class)
    EnterpriseDataReadPolicyResolver disabledEnterpriseDataReadPolicyResolver() {
        return new DisabledEnterpriseDataReadPolicyResolver();
    }

}
