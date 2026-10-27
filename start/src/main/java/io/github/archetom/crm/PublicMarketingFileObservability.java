package io.github.archetom.crm;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Fixed-cardinality metric mirroring the public FILE lane health state. */
@Component
public class PublicMarketingFileObservability {
    PublicMarketingFileObservability(MeterRegistry meterRegistry,
                                     PublicMarketingFileHealthIndicator healthIndicator) {
        for (PublicMarketingFileHealthIndicator.LaneState state
                : PublicMarketingFileHealthIndicator.LaneState.values()) {
            Gauge.builder("atom.public_marketing_file.lane_state", healthIndicator,
                            value -> value.state() == state ? 1 : 0)
                    .tag("lane", "public_marketing_file")
                    .tag("state", state.name())
                    .register(meterRegistry);
        }
    }
}
