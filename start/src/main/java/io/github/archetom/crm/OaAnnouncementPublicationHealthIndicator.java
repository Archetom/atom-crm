package io.github.archetom.crm;

import io.github.archetom.crm.application.properties.OaAnnouncementPublicationProperties;
import io.github.archetom.crm.application.properties.SchedulingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Health of the OA local publication lane, not a promise for a future authorization outcome. */
@Component("oaAnnouncementPublication")
@RequiredArgsConstructor
public class OaAnnouncementPublicationHealthIndicator implements HealthIndicator {
    private final OaAnnouncementPublicationProperties properties;
    private final SchedulingProperties schedulingProperties;
    private final OaAnnouncementPublicationRuntimeStatus runtimeStatus;

    @Override
    public Health health() {
        if (!properties.isEnabled()) return Health.up().withDetail("state", "DISABLED").build();
        if (!properties.valid()) return Health.down().withDetail("state", "CONFIG_INVALID").build();
        if (!schedulingProperties.isPulsesEnabled()) {
            return Health.down().withDetail("state", "SCHEDULER_DISABLED").build();
        }
        if (runtimeStatus.failedSinceLastSuccess()) {
            return Health.down().withDetail("state", "RUNTIME_FAILURE").build();
        }
        return Health.up().withDetail("state", "READY").build();
    }
}
