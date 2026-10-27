package io.github.archetom.crm;

import io.github.archetom.crm.application.automation.BackgroundWorkObservability;
import io.github.archetom.crm.application.automation.AutomationPolicy;
import io.github.archetom.crm.application.automation.AutomationSchedulingReadiness;
import io.github.archetom.crm.application.port.out.automation.AutomationScheduleStore;
import io.github.archetom.crm.application.port.out.automation.WorkRecurrenceControlStore;
import io.github.archetom.crm.application.properties.SchedulingProperties;
import io.github.archetom.crm.application.properties.WorkRecurrenceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/** Establishes the durable deployment revision before the enabled lane can claim occurrences. */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@Slf4j
public class AutomationCadenceReconciliation {
    private final SchedulingProperties properties;
    private final WorkRecurrenceProperties recurrenceProperties;
    private final AutomationScheduleStore store;
    private final WorkRecurrenceControlStore recurrenceControl;
    private final BackgroundWorkObservability observability;

    @Bean
    ApplicationRunner reconcileAutomationCadenceAtStartup() {
        return arguments -> reconcileAutomationCadence();
    }

    /** Resumes a durable bounded recurrence reconciliation after restart or a partial batch. */
    @Scheduled(fixedDelayString = "#{@workRecurrenceReconciliationDelay.milliseconds()}")
    public void continueWorkRecurrenceReconciliation() {
        AutomationPolicy policy = properties.policy();
        if (!policy.enabled() || !policy.valid()
                || store.schedulingReadiness(policy) != AutomationSchedulingReadiness.READY) return;
        var recurrencePolicy = recurrenceProperties.policy();
        if (!properties.recurrenceValid() || !recurrencePolicy.enabled() || !recurrencePolicy.valid()) return;
        try {
            recurrenceControl.reconcile(recurrencePolicy);
        } catch (RuntimeException exception) {
            log.warn("Work recurrence reconciliation failed");
        }
    }

    private void reconcileAutomationCadence() {
        AutomationPolicy policy = properties.policy();
        if (!policy.enabled() || !policy.valid()) return;
        try {
            var result = store.reconcileCadenceRevision(policy);
            result.transitions().forEach(transition -> observability.recordAutomationTransition(
                    transition.kind(), transition.metric(), transition.count()));
            observability.recordAutomationSnapshot(store.schedulingSnapshot());
            if (result.readiness() == AutomationSchedulingReadiness.REVISION_DRIFT) {
                log.warn("Scheduled automation cadence is not ready");
            }
            var recurrencePolicy = recurrenceProperties.policy();
            if (result.readiness() == AutomationSchedulingReadiness.READY && recurrencePolicy.enabled()) {
                var readiness = recurrenceControl.reconcile(recurrencePolicy);
                if (readiness != io.github.archetom.crm.application.automation.WorkRecurrenceReadiness.READY) {
                    log.warn("Work recurrence is not ready");
                }
            }
        } catch (RuntimeException exception) {
            log.warn("Scheduled automation cadence reconciliation failed");
        }
    }
}
