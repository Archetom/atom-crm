package io.github.archetom.crm.application.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Default-off local wake-up policy for durable B23 adoption-provenance retention. */
@Data
@ConfigurationProperties(prefix = "atom.enterprise-data.adoption-retention")
public class EnterpriseDataAdoptionRetentionProperties {
    private boolean enabled = false;
    private int concurrency = 1;
    private int batchLimit = 100;
    private Duration leaseDuration = Duration.ofMinutes(2);

    public boolean valid() {
        return concurrency >= 1 && concurrency <= SchedulingProperties.MAX_LANE_CONCURRENCY
                && batchLimit >= 1 && batchLimit <= 500
                && leaseDuration != null && leaseDuration.compareTo(Duration.ofSeconds(1)) >= 0
                && leaseDuration.compareTo(Duration.ofMinutes(30)) <= 0;
    }
}
