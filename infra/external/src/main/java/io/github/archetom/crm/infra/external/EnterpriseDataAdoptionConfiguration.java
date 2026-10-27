package io.github.archetom.crm.infra.external;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAdoptionPolicyResolver;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataPreviewStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Adoption remains independently fail-closed even when a deployment enables a read adapter. */
@Configuration(proxyBeanMethods = false)
public class EnterpriseDataAdoptionConfiguration {
    @Bean
    @ConditionalOnMissingBean(EnterpriseDataAdoptionPolicyResolver.class)
    EnterpriseDataAdoptionPolicyResolver disabledEnterpriseDataAdoptionPolicyResolver() {
        return new DisabledEnterpriseDataAdoptionPolicyResolver();
    }

    @Bean
    @ConditionalOnMissingBean(EnterpriseDataPreviewStore.class)
    EnterpriseDataPreviewStore enterpriseDataPreviewStore() {
        return new InMemoryEnterpriseDataPreviewStore();
    }
}
