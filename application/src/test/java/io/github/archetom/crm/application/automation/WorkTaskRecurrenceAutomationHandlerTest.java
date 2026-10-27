package io.github.archetom.crm.application.automation;

import io.github.archetom.crm.application.port.out.automation.AutomationScheduleStore;
import io.github.archetom.crm.application.port.out.automation.WorkRecurrenceControlStore;
import io.github.archetom.crm.application.properties.SchedulingProperties;
import io.github.archetom.crm.application.properties.WorkRecurrenceProperties;
import io.github.archetom.crm.application.service.WorkTaskRecurrenceService;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkTaskRecurrenceAutomationHandlerTest {
    @Test
    void locksAndVerifiesExactRecurrenceControlBeforeMaterializing() {
        WorkTaskRecurrenceService recurrenceService = mock(WorkTaskRecurrenceService.class);
        AutomationScheduleStore store = mock(AutomationScheduleStore.class);
        WorkRecurrenceControlStore control = mock(WorkRecurrenceControlStore.class);
        WorkRecurrenceProperties recurrenceProperties = new WorkRecurrenceProperties();
        AutomationExecutionContext context = context();
        LocalDateTime committedTime = context.evaluationTime().plusSeconds(3);
        when(control.readiness(any())).thenReturn(WorkRecurrenceReadiness.READY);
        when(store.requireCurrent(context)).thenReturn(committedTime);
        when(recurrenceService.materialize(eq(context), eq(100), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<LocalDateTime> fence = invocation.getArgument(2, Supplier.class);
                    assertEquals(committedTime, fence.get());
                    return 1;
                });

        AutomationOutcome outcome = new WorkTaskRecurrenceAutomationHandler(recurrenceService, store,
                new SchedulingProperties(), recurrenceProperties, control).execute(context);

        assertEquals(AutomationOutcome.released(1), outcome);
        var order = inOrder(control, store);
        order.verify(control).requireCurrent(recurrenceProperties.policy());
        order.verify(store).requireCurrent(context);
    }

    @Test
    void releasesClaimWithoutMaterializingWhenControlIsNotReady() {
        WorkTaskRecurrenceService recurrenceService = mock(WorkTaskRecurrenceService.class);
        WorkRecurrenceControlStore control = mock(WorkRecurrenceControlStore.class);
        when(control.readiness(any())).thenReturn(WorkRecurrenceReadiness.TZDB_DRIFT);

        AutomationOutcome outcome = new WorkTaskRecurrenceAutomationHandler(recurrenceService,
                mock(AutomationScheduleStore.class), new SchedulingProperties(), new WorkRecurrenceProperties(), control)
                .execute(context());

        assertEquals(AutomationOutcome.released(0), outcome);
        verifyNoInteractions(recurrenceService);
    }

    @Test
    void invalidRecurrenceConfigurationClosesOnlyThisKind() {
        WorkTaskRecurrenceService recurrenceService = mock(WorkTaskRecurrenceService.class);
        SchedulingProperties properties = new SchedulingProperties();
        properties.setRecurrenceRecordLimit(0);

        AutomationOutcome outcome = new WorkTaskRecurrenceAutomationHandler(recurrenceService,
                mock(AutomationScheduleStore.class), properties, new WorkRecurrenceProperties(),
                mock(WorkRecurrenceControlStore.class)).execute(context());

        assertEquals(AutomationOutcome.released(0), outcome);
        verifyNoInteractions(recurrenceService);
    }

    private static AutomationExecutionContext context() {
        LocalDateTime evaluation = LocalDateTime.of(2026, 11, 1, 6, 15);
        return new AutomationExecutionContext(7L, new TenantId(8L), AutomationKind.WORK_TASK_RECURRENCE,
                1L, UUID.randomUUID(), 1L, 1, evaluation.minusMinutes(5), evaluation);
    }
}
