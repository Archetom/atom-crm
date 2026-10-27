package io.github.archetom.crm.application.automation;

import io.github.archetom.crm.application.port.out.automation.AutomationScheduleStore;
import io.github.archetom.crm.application.properties.SchedulingProperties;
import io.github.archetom.crm.application.transaction.AfterCommitExecutor;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Keeps pool configuration and its derived fixed schedule in one transaction. */
@Component
@RequiredArgsConstructor
public class AutomationScheduleManager {
    private final AutomationScheduleStore store;
    private final SchedulingProperties properties;
    private final AfterCommitExecutor afterCommitExecutor;
    private final BackgroundWorkObservability observability;

    /** Must follow the owning customization guard and precede the pool-root lock. */
    public void preparePoolConfiguration(TenantId tenantId, AutomationKind kind, boolean createIfMissing) {
        store.preparePoolConfiguration(tenantId, kind, createIfMissing, properties.policy());
    }

    public void synchronize(TenantId tenantId, AutomationKind kind, boolean active) {
        var transitions = store.synchronize(tenantId, kind, active, properties.policy());
        recordTransitions(transitions);
    }

    /** Keeps the claimed run leased until the enclosing effect records SUCCESS. */
    public void synchronizeCurrentRun(AutomationExecutionContext context, boolean active) {
        var transitions = store.synchronizeCurrentRun(context, active, properties.policy());
        recordTransitions(transitions);
    }

    private void recordTransitions(java.util.List<AutomationRunTransition> transitions) {
        if (!transitions.isEmpty()) {
            afterCommitExecutor.execute("automation schedule transition metrics", () -> transitions.forEach(transition ->
                    observability.recordAutomationTransition(transition.kind(), transition.metric(), transition.count())));
        }
    }
}
