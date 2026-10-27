package io.github.archetom.crm.infra.external;

import io.github.archetom.crm.application.port.out.RegionReferenceCatalog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Supplies the empty catalog unless a deployment has installed its own curated region data. */
@Configuration(proxyBeanMethods = false)
public class RegionReferenceCatalogConfiguration {

    @Bean
    @ConditionalOnMissingBean(RegionReferenceCatalog.class)
    RegionReferenceCatalog emptyRegionReferenceCatalog() {
        return new EmptyRegionReferenceCatalog();
    }
}
