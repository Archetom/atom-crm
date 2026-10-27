package io.github.archetom.crm.application.automation;

import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.automation.AutomationScheduleStore;
import io.github.archetom.crm.application.properties.SchedulingProperties;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.domain.valueobject.TenantId;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

class AutomationRunPumpTest {
    @Test
    void commitsTheExactClaimedHandlerAndRunSuccessTogether() {
        AutomationScheduleStore store = mock(AutomationScheduleStore.class);
        AutomationHandler customer = handler(AutomationKind.CUSTOMER_POOL_RELEASE);
        AutomationHandler contact = handler(AutomationKind.CONTACT_POOL_RELEASE);
        AutomationHandler recurrence = handler(AutomationKind.WORK_TASK_RECURRENCE);
        AutomationExecutionContext context = context(AutomationKind.CUSTOMER_POOL_RELEASE);
        ready(store);
        when(store.claimOne(any(), any(), any())).thenReturn(new AutomationClaimResult(Optional.of(context), List.of()));
        when(customer.execute(context)).thenReturn(AutomationOutcome.released(3));
        when(store.succeed(eq(context), eq(AutomationOutcome.released(3))))
                .thenReturn(new AutomationRunTransition(context.kind(), AutomationRunMetric.SUCCEEDED, 1));

        pump(store, customer, contact, recurrence).runOnce();

        verify(store).materializeOne(any(), any());
        verify(customer).execute(context);
        verify(store).succeed(eq(context), eq(AutomationOutcome.released(3)));
        verify(store, never()).recover(eq(context), any());
    }

    @Test
    void retriesOnlyTheSameClaimedOccurrenceWhenTheBusinessTransactionRollsBack() {
        AutomationScheduleStore store = mock(AutomationScheduleStore.class);
        AutomationHandler customer = handler(AutomationKind.CUSTOMER_POOL_RELEASE);
        AutomationHandler contact = handler(AutomationKind.CONTACT_POOL_RELEASE);
        AutomationHandler recurrence = handler(AutomationKind.WORK_TASK_RECURRENCE);
        AutomationExecutionContext context = context(AutomationKind.CUSTOMER_POOL_RELEASE);
        ready(store);
        when(store.claimOne(any(), any(), any())).thenReturn(new AutomationClaimResult(Optional.of(context), List.of()));
        when(customer.execute(context)).thenThrow(new IllegalStateException("failure"));
        when(store.recover(eq(context), any()))
                .thenReturn(new AutomationRunTransition(context.kind(), AutomationRunMetric.RETRY, 1));

        pump(store, customer, contact, recurrence).runOnce();

        verify(store, never()).succeed(any(), any());
        verify(store).recover(eq(context), any());
    }

    @Test
    void backgroundTemplatePolicyNeverLogsTheHandlerExceptionType() {
        AutomationScheduleStore store = mock(AutomationScheduleStore.class);
        AutomationHandler customer = handler(AutomationKind.CUSTOMER_POOL_RELEASE);
        AutomationHandler contact = handler(AutomationKind.CONTACT_POOL_RELEASE);
        AutomationHandler recurrence = handler(AutomationKind.WORK_TASK_RECURRENCE);
        AutomationExecutionContext context = context(AutomationKind.CUSTOMER_POOL_RELEASE);
        ready(store);
        when(store.claimOne(any(), any(), any())).thenReturn(new AutomationClaimResult(Optional.of(context), List.of()));
        when(customer.execute(context)).thenThrow(new UnsafeAutomationFailure());
        when(store.recover(eq(context), any()))
                .thenReturn(new AutomationRunTransition(context.kind(), AutomationRunMetric.RETRY, 1));
        Logger logger = (Logger) LoggerFactory.getLogger(
                "io.github.archetom.crm.application.service.template.OperationTemplateSupport");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            pump(store, customer, contact, recurrence).runOnce();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertFalse(appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("UnsafeAutomationFailure") || message.contains("exceptionType=")));
    }

    @Test
    void rejectsAnIncompleteClosedCatalog() {
        assertThrows(IllegalStateException.class,
                () -> new AutomationHandlerRegistry(List.of(handler(AutomationKind.CUSTOMER_POOL_RELEASE))));
    }

    @Test
    void excludesANotReadyRecurrenceKindBeforeMaterializingOrClaiming() {
        AutomationScheduleStore store = mock(AutomationScheduleStore.class);
        AutomationHandler customer = handler(AutomationKind.CUSTOMER_POOL_RELEASE);
        AutomationHandler contact = handler(AutomationKind.CONTACT_POOL_RELEASE);
        AutomationHandler recurrence = handler(AutomationKind.WORK_TASK_RECURRENCE);
        when(recurrence.acceptsNewRuns()).thenReturn(false);
        ready(store);

        pump(store, customer, contact, recurrence).runOnce();

        verify(store).materializeOne(any(), argThat(kinds -> !kinds.contains(AutomationKind.WORK_TASK_RECURRENCE)));
        verify(store).claimOne(any(), any(), argThat(kinds -> !kinds.contains(AutomationKind.WORK_TASK_RECURRENCE)));
    }

    @Test
    void isolatesARecurrenceReadinessProbeFailureFromOtherAutomationKinds() {
        AutomationScheduleStore store = mock(AutomationScheduleStore.class);
        AutomationHandler customer = handler(AutomationKind.CUSTOMER_POOL_RELEASE);
        AutomationHandler contact = handler(AutomationKind.CONTACT_POOL_RELEASE);
        AutomationHandler recurrence = handler(AutomationKind.WORK_TASK_RECURRENCE);
        when(recurrence.acceptsNewRuns()).thenThrow(new IllegalStateException("control unavailable"));
        ready(store);

        pump(store, customer, contact, recurrence).runOnce();

        verify(store).materializeOne(any(), argThat(kinds -> kinds.contains(AutomationKind.CUSTOMER_POOL_RELEASE)
                && kinds.contains(AutomationKind.CONTACT_POOL_RELEASE)
                && !kinds.contains(AutomationKind.WORK_TASK_RECURRENCE)));
        verify(store).claimOne(any(), any(), argThat(kinds -> !kinds.contains(AutomationKind.WORK_TASK_RECURRENCE)));
    }

    private AutomationRunPump pump(AutomationScheduleStore store, AutomationHandler customer,
                                   AutomationHandler contact, AutomationHandler recurrence) {
        SchedulingProperties properties = new SchedulingProperties();
        properties.setEnabled(true);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new AutomationRunPump(store, new AutomationHandlerRegistry(List.of(customer, contact, recurrence)), properties,
                new CommandServiceTemplate("test-app", transactions), mock(BackgroundWorkObservability.class));
    }

    private static void ready(AutomationScheduleStore store) {
        when(store.schedulingReadiness(any())).thenReturn(AutomationSchedulingReadiness.READY);
        when(store.materializeOne(any(), any())).thenReturn(Optional.empty());
        when(store.claimOne(any(), any(), any())).thenReturn(AutomationClaimResult.empty());
        when(store.schedulingSnapshot()).thenReturn(new AutomationSchedulingSnapshot(List.of(), List.of(), java.util.Map.of()));
    }

    private static AutomationHandler handler(AutomationKind kind) {
        AutomationHandler handler = mock(AutomationHandler.class);
        when(handler.kind()).thenReturn(kind);
        when(handler.acceptsNewRuns()).thenReturn(true);
        when(handler.operation()).thenReturn(kind == AutomationKind.CUSTOMER_POOL_RELEASE
                ? UseCaseOperation.CUSTOMER_POOL_AUTOMATION : kind == AutomationKind.CONTACT_POOL_RELEASE
                ? UseCaseOperation.CONTACT_POOL_AUTOMATION : UseCaseOperation.WORK_TASK_RECURRENCE_AUTOMATION);
        return handler;
    }

    private static AutomationExecutionContext context(AutomationKind kind) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 21, 12, 0);
        return new AutomationExecutionContext(81L, new TenantId(19L), kind, 2L,
                UUID.randomUUID(), 4L, 1, now.minusHours(1), now);
    }

    private static final class UnsafeAutomationFailure extends RuntimeException {
    }
}
