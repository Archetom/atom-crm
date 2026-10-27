package io.github.archetom.crm.application.vo;

import java.util.List;

/** Caller-specific actions and blockers resolved by the Work task series use case. */
public record WorkTaskSeriesCapabilityVO(List<String> allowedActions, List<String> actionBlockers) {
    public WorkTaskSeriesCapabilityVO {
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
        actionBlockers = actionBlockers == null ? List.of() : List.copyOf(actionBlockers);
    }

    public static WorkTaskSeriesCapabilityVO none() {
        return new WorkTaskSeriesCapabilityVO(List.of(), List.of());
    }
}
