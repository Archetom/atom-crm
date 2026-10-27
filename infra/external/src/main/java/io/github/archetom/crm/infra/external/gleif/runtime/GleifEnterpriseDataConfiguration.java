package io.github.archetom.crm.infra.external.gleif.runtime;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataReadPolicy;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataReadPolicyResolver;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSharedQuota;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSource;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Explicit, fail-closed GLEIF runtime composition. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "atom.enterprise-data.gleif", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "atom.enterprise-data.companies-house", name = "enabled", havingValue = "false", matchIfMissing = true)
@EnableConfigurationProperties(GleifEnterpriseDataProperties.class)
public class GleifEnterpriseDataConfiguration {

    @Bean
    @ConditionalOnMissingBean(GleifSharedQuota.class)
    GleifSharedQuota gleifSharedQuota(ObjectProvider<EnterpriseDataSharedQuota> quotas) {
        EnterpriseDataSharedQuota quota = quotas.getIfAvailable();
        if (quota == null) return new UnavailableGleifSharedQuota();
        return new GleifSharedQuota() {
            @Override public boolean ready() { return quota.ready(); }
            @Override public boolean tryAcquire(TenantId tenantId, String sourceKey,
                                                java.time.Instant now, int maximumRequests,
                                                java.time.Duration window) {
                return quota.tryAcquire(tenantId, sourceKey, now, maximumRequests, window);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(EnterpriseDataSource.class)
    EnterpriseDataSource gleifEnterpriseDataSource(GleifEnterpriseDataProperties properties,
                                                   GleifSharedQuota sharedQuota,
                                                   ObjectProvider<GleifConformanceReportSource> reports) {
        return new GleifEnterpriseDataSource(properties, sharedQuota,
                new GleifHttpTransport.JavaHttpTransport(java.net.http.HttpClient.newBuilder()
                        .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build()), new tools.jackson.databind.ObjectMapper(),
                java.time.Clock.systemUTC(), reports.getIfAvailable(), false);
    }

    @Bean
    @ConditionalOnMissingBean(EnterpriseDataReadPolicyResolver.class)
    EnterpriseDataReadPolicyResolver gleifEnterpriseDataReadPolicyResolver(
            GleifEnterpriseDataProperties properties, GleifSharedQuota sharedQuota,
            ObjectProvider<GleifConformanceReportSource> reports) {
        return tenantId -> policy(properties, sharedQuota, reports.getIfAvailable(), tenantId);
    }

    private static EnterpriseDataReadPolicy policy(GleifEnterpriseDataProperties properties,
                                                    GleifSharedQuota sharedQuota,
                                                    GleifConformanceReportSource reports, TenantId tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("tenant must not be null");
        return GleifConformanceManifest.current(reports, java.time.Instant.now())
                ? GleifSourcePolicyCatalog.resolve(properties, sharedQuota, tenantId).policy()
                : EnterpriseDataReadPolicy.unavailable();
    }

    @Bean
    @ConditionalOnProperty(prefix = "atom.enterprise-data.gleif", name = "conformance-report")
    GleifConformanceReportSource gleifConformanceReportSource(GleifEnterpriseDataProperties properties) {
        return new FileGleifConformanceReportSource(properties.getConformanceReport());
    }
}
