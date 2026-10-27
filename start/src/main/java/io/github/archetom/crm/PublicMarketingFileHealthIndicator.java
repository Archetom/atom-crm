package io.github.archetom.crm;

import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.properties.PublicMarketingFileProperties;
import io.github.archetom.crm.application.service.PublicMarketingFileRuntimeStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Health for the anonymous FILE lane only; it exposes no tenant, form, draft, file, or exception. */
@Component("publicMarketingFile")
@RequiredArgsConstructor
public class PublicMarketingFileHealthIndicator implements HealthIndicator {
    private final PublicMarketingFileProperties properties;
    private final AttachmentRuntimePolicy runtimePolicy;
    private final PublicMarketingFileRuntimeStatus runtimeStatus;

    @Override
    public Health health() {
        LaneState state = state();
        Health.Builder builder = state == LaneState.DISABLED || state == LaneState.READY
                ? Health.up() : Health.down();
        return builder.withDetail("state", state.name()).build();
    }

    LaneState state() {
        if (!properties.isEnabled()) return LaneState.DISABLED;
        if (!properties.valid()) return LaneState.CONFIG_INVALID;
        if (!runtimePolicy.enabled()) return LaneState.FILES_NOT_READY;
        return switch (runtimeStatus.state()) {
            case FILES_NOT_READY -> LaneState.FILES_NOT_READY;
            case RUNTIME_FAILURE -> LaneState.RUNTIME_FAILURE;
            // No successful readiness probe has been recorded after process start, so never
            // advertise the anonymous lane as usable merely because the global switch is on.
            case UNKNOWN -> LaneState.FILES_NOT_READY;
            case READY -> LaneState.READY;
        };
    }

    enum LaneState {
        DISABLED, CONFIG_INVALID, FILES_NOT_READY, RUNTIME_FAILURE, READY
    }
}
