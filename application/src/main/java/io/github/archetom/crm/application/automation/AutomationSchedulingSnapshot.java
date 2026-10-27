package io.github.archetom.crm.application.automation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Bounded control-plane census used only for safe operational gauges. */
public record AutomationSchedulingSnapshot(List<AutomationRunStateCount> runStates,
                                           List<AutomationScheduleStateCount> scheduleStates,
                                           Map<AutomationKind, LocalDateTime> latestSuccessUtc) {
    public AutomationSchedulingSnapshot {
        runStates = runStates == null ? List.of() : List.copyOf(runStates);
        scheduleStates = scheduleStates == null ? List.of() : List.copyOf(scheduleStates);
        latestSuccessUtc = latestSuccessUtc == null ? Map.of() : Map.copyOf(latestSuccessUtc);
    }
}
