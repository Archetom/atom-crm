package io.github.archetom.crm.application.automation;

import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.automation.AutomationScheduleStore;
import io.github.archetom.crm.application.port.out.automation.WorkRecurrenceControlStore;
import io.github.archetom.crm.application.properties.SchedulingProperties;
import io.github.archetom.crm.application.properties.WorkRecurrenceProperties;
import io.github.archetom.crm.application.service.WorkTaskRecurrenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkTaskRecurrenceAutomationHandler implements AutomationHandler {
    private final WorkTaskRecurrenceService recurrenceService;
    private final AutomationScheduleStore store;
    private final SchedulingProperties properties;
    private final WorkRecurrenceProperties recurrenceProperties;
    private final WorkRecurrenceControlStore recurrenceControl;

    @Override public AutomationKind kind() { return AutomationKind.WORK_TASK_RECURRENCE; }
    @Override public UseCaseOperation operation() { return UseCaseOperation.WORK_TASK_RECURRENCE_AUTOMATION; }
    @Override public boolean acceptsNewRuns() {
        return properties.recurrenceValid()
                && recurrenceControl.readiness(recurrenceProperties.policy()) == WorkRecurrenceReadiness.READY;
    }
    @Override public AutomationOutcome execute(AutomationExecutionContext context) {
        if (!acceptsNewRuns()) {
            // An existing active schedule can outlive a later readiness drift. Settle its
            // claimed occurrence harmlessly so it cannot consume retries or block pool kinds.
            return AutomationOutcome.released(0);
        }
        int created = recurrenceService.materialize(context, properties.getRecurrenceRecordLimit(), () -> {
                    // The materializer has already locked the tenant. Keep every
                    // work-recurrence mutation on tenant -> control -> schedule/run -> series.
                    recurrenceControl.requireCurrent(recurrenceProperties.policy());
                    return store.requireCurrent(context);
                });
        return AutomationOutcome.released(created);
    }
}
