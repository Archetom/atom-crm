package io.github.archetom.crm.infra.external;

import io.github.archetom.crm.application.port.out.notification.NotificationChannelAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Supplies the fail-closed EMAIL adapter unless a deployment installs one explicit channel adapter. */
@Configuration(proxyBeanMethods = false)
public class NotificationChannelAdapterConfiguration {
    @Bean
    @ConditionalOnMissingBean(NotificationChannelAdapter.class)
    NotificationChannelAdapter disabledEmailNotificationChannelAdapter() {
        return new DisabledEmailNotificationChannelAdapter();
    }
}
