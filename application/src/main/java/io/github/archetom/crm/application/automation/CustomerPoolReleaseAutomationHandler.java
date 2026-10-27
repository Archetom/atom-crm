package io.github.archetom.crm.application.automation;

import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.automation.AutomationScheduleStore;
import io.github.archetom.crm.application.properties.SchedulingProperties;
import io.github.archetom.crm.application.service.CustomerPoolAutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomerPoolReleaseAutomationHandler implements AutomationHandler {
    private final CustomerPoolAutomationService releaseService;
    private final AutomationScheduleStore store;
    private final SchedulingProperties properties;

    @Override public AutomationKind kind() { return AutomationKind.CUSTOMER_POOL_RELEASE; }

    @Override public UseCaseOperation operation() { return UseCaseOperation.CUSTOMER_POOL_AUTOMATION; }

    @Override
    public AutomationOutcome execute(AutomationExecutionContext context) {
        int changed = releaseService.runSystem(context.tenantId(), context.evaluationTime(),
                properties.policy().poolRecordLimit(), () -> store.requireCurrent(context));
        return AutomationOutcome.released(changed);
    }
}
