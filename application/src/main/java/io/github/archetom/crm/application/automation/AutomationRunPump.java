package io.github.archetom.crm.application.automation;

import io.github.archetom.crm.application.port.out.automation.AutomationScheduleStore;
import io.github.archetom.crm.application.properties.SchedulingProperties;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.OperationLogPolicy;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Claims at most one durable B26 run per pulse. */
@Component
@Slf4j
public class AutomationRunPump implements BackgroundWorkPump {
    private final AutomationScheduleStore store;
    private final AutomationHandlerRegistry handlers;
    private final SchedulingProperties properties;
    private final CommandServiceTemplate commandTemplate;
    private final BackgroundWorkObservability observability;
    private final UUID leaseOwner = UUID.randomUUID();

    public AutomationRunPump(AutomationScheduleStore store, AutomationHandlerRegistry handlers,
                             SchedulingProperties properties, CommandServiceTemplate commandTemplate,
                             BackgroundWorkObservability observability) {
        this.store = store;
        this.handlers = handlers;
        this.properties = properties;
        this.commandTemplate = commandTemplate;
        this.observability = observability;
    }

    @Override
    public void runOnce() {
        AutomationPolicy policy = properties.policy();
        if (!policy.enabled() || !policy.valid()) return;
        if (store.schedulingReadiness(policy) != AutomationSchedulingReadiness.READY) return;
        Set<AutomationKind> eligibleKinds = handlers.kindsAcceptingNewRuns();
        store.materializeOne(policy, eligibleKinds).ifPresent(materialized -> {
            observability.recordAutomationTransition(materialized.kind(), AutomationRunMetric.MATERIALIZED, 1);
            observability.recordAutomationDueLag(materialized.kind(), materialized.dueLag());
        });
        AutomationClaimResult claim = store.claimOne(policy, leaseOwner, eligibleKinds);
        recordTransitions(claim.transitions());
        Optional<AutomationExecutionContext> claimed = claim.context();
        if (claimed.isEmpty()) {
            store.purgeTerminal(policy);
            observeSnapshot();
            return;
        }
        AutomationExecutionContext context = claimed.get();
        observability.recordAutomationTransition(context.kind(), AutomationRunMetric.CLAIMED, 1);
        AutomationHandler handler = handlers.require(context.kind());
        AtomicReference<AutomationRunTransition> successTransition = new AtomicReference<>();
        Result<AutomationOutcome> result = commandTemplate.execute(handler.operation(), new ServiceOperation<>() {
            @Override
            public AutomationOutcome execute() {
                AutomationOutcome outcome = handler.execute(context);
                successTransition.set(store.succeed(context, outcome));
                return outcome;
            }
        }, OperationLogPolicy.SAFE_BACKGROUND);
        if (!result.isSuccess()) {
            // Keep logs and the durable ledger free of record IDs and exception text.
            log.warn("Scheduled automation execution failed: kind={}, runId={}, attempt={}",
                    context.kind(), context.runId(), context.attempt());
            recordTransition(store.recover(context, policy));
        } else {
            recordTransition(successTransition.get());
            observability.recordAutomationAffected(context.kind(), result.getData().affectedCount());
        }
        store.purgeTerminal(policy);
        observeSnapshot();
    }

    private void recordTransitions(Iterable<AutomationRunTransition> transitions) {
        transitions.forEach(this::recordTransition);
    }

    private void recordTransition(AutomationRunTransition transition) {
        observability.recordAutomationTransition(transition.kind(), transition.metric(), transition.count());
    }

    private void observeSnapshot() {
        try {
            observability.recordAutomationSnapshot(store.schedulingSnapshot());
        } catch (RuntimeException exception) {
            log.warn("Scheduled automation observation failed");
        }
    }
}
