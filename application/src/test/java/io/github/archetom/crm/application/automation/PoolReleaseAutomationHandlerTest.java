package io.github.archetom.crm.application.automation;

import io.github.archetom.crm.application.port.out.automation.AutomationScheduleStore;
import io.github.archetom.crm.application.properties.SchedulingProperties;
import io.github.archetom.crm.application.service.ContactPoolAutomationService;
import io.github.archetom.crm.application.service.CustomerPoolAutomationService;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PoolReleaseAutomationHandlerTest {
    @Test
    void customerHandlerPassesOnlyTheClaimedTenantEvaluationAndFence() {
        CustomerPoolAutomationService service = mock(CustomerPoolAutomationService.class);
        AutomationScheduleStore store = mock(AutomationScheduleStore.class);
        AutomationExecutionContext context = context(AutomationKind.CUSTOMER_POOL_RELEASE);
        LocalDateTime databaseTime = context.evaluationTime().plusSeconds(5);
        when(store.requireCurrent(context)).thenReturn(databaseTime);
        when(service.runSystem(eq(context.tenantId()), eq(context.evaluationTime()), eq(200), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<LocalDateTime> verifiedTime = invocation.getArgument(3, Supplier.class);
                    assertEquals(databaseTime, verifiedTime.get());
                    return 4;
                });

        AutomationOutcome outcome = new CustomerPoolReleaseAutomationHandler(
                service, store, new SchedulingProperties()).execute(context);

        assertEquals(AutomationOutcome.released(4), outcome);
        verify(store).requireCurrent(context);
    }

    @Test
    void contactHandlerUsesItsOwnServiceAndNeverAHumanCaller() {
        ContactPoolAutomationService service = mock(ContactPoolAutomationService.class);
        AutomationScheduleStore store = mock(AutomationScheduleStore.class);
        AutomationExecutionContext context = context(AutomationKind.CONTACT_POOL_RELEASE);
        when(store.requireCurrent(context)).thenReturn(context.evaluationTime());
        when(service.runSystem(eq(context.tenantId()), eq(context.evaluationTime()), eq(200), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<LocalDateTime> verifiedTime = invocation.getArgument(3, Supplier.class);
                    assertEquals(context.evaluationTime(), verifiedTime.get());
                    return 2;
                });

        AutomationOutcome outcome = new ContactPoolReleaseAutomationHandler(
                service, store, new SchedulingProperties()).execute(context);

        assertEquals(AutomationOutcome.released(2), outcome);
        verify(store).requireCurrent(context);
    }

    private static AutomationExecutionContext context(AutomationKind kind) {
        LocalDateTime evaluation = LocalDateTime.of(2026, 7, 21, 12, 0);
        return new AutomationExecutionContext(7L, new TenantId(8L), kind, 1L,
                UUID.randomUUID(), 1L, 1, evaluation.minusHours(1), evaluation);
    }
}
