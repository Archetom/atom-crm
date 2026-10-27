package io.github.archetom.crm.application.config;

import io.github.archetom.crm.application.properties.SchedulingProperties;
import io.github.archetom.crm.application.properties.WorkRecurrenceProperties;
import io.github.archetom.crm.application.properties.NotificationReminderProperties;
import io.github.archetom.crm.application.properties.NotificationDeliveryProperties;
import io.github.archetom.crm.application.properties.OaAnnouncementPublicationProperties;
import io.github.archetom.crm.application.properties.EnterpriseDataAdoptionRetentionProperties;
import io.github.archetom.crm.application.properties.PublicMarketingFileProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SchedulingProperties.class, WorkRecurrenceProperties.class,
        NotificationReminderProperties.class, NotificationDeliveryProperties.class,
        OaAnnouncementPublicationProperties.class, EnterpriseDataAdoptionRetentionProperties.class,
        PublicMarketingFileProperties.class})
public class AutomationConfiguration {
}
