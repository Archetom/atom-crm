package io.github.archetom.crm;

import io.github.archetom.crm.application.properties.SchedulingProperties;
import io.github.archetom.crm.application.automation.AutomationSchedulingReadiness;
import io.github.archetom.crm.application.automation.WorkRecurrenceReadiness;
import io.github.archetom.crm.application.port.out.automation.AutomationScheduleStore;
import io.github.archetom.crm.application.port.out.automation.WorkRecurrenceControlStore;
import io.github.archetom.crm.application.properties.WorkRecurrenceProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AutomationSchedulingHealthIndicatorTest {
    @Test
    void disabledSchedulingIsAnExplicitHealthyState() {
        assertEquals("UP", indicator(new SchedulingProperties(), mock(AutomationScheduleStore.class),
                mock(WorkRecurrenceControlStore.class))
                .health().getStatus().getCode());
    }

    @Test
    void enabledInvalidSchedulingDegradesActuatorHealth() {
        SchedulingProperties properties = new SchedulingProperties();
        properties.setEnabled(true);
        properties.setPoolRecordLimit(0);

        assertEquals("DOWN", indicator(properties, mock(AutomationScheduleStore.class), mock(WorkRecurrenceControlStore.class))
                .health().getStatus().getCode());
    }

    @Test
    void revisionDriftIsNotReadyEvenWhenThePropertyValuesAreValid() {
        SchedulingProperties properties = new SchedulingProperties();
        properties.setEnabled(true);
        AutomationScheduleStore store = mock(AutomationScheduleStore.class);
        when(store.schedulingReadiness(any())).thenReturn(AutomationSchedulingReadiness.REVISION_DRIFT);

        var health = indicator(properties, store, mock(WorkRecurrenceControlStore.class)).health();

        assertEquals("DOWN", health.getStatus().getCode());
        assertEquals("NOT_READY", health.getDetails().get("state"));
    }

    @Test
    void recurrenceDriftDegradesHealthInsteadOfReportingReady() {
        SchedulingProperties properties = new SchedulingProperties();
        properties.setEnabled(true);
        AutomationScheduleStore store = mock(AutomationScheduleStore.class);
        WorkRecurrenceControlStore recurrenceControl = mock(WorkRecurrenceControlStore.class);
        when(store.schedulingReadiness(any())).thenReturn(AutomationSchedulingReadiness.READY);
        when(recurrenceControl.readiness(any())).thenReturn(WorkRecurrenceReadiness.TZDB_DRIFT);

        var health = indicator(properties, store, recurrenceControl).health();

        assertEquals("DOWN", health.getStatus().getCode());
        assertEquals("RECURRENCE_NOT_READY", health.getDetails().get("state"));
        assertEquals("TZDB_DRIFT", health.getDetails().get("recurrenceState"));
    }

    @Test
    void invalidRecurrenceConfigurationDoesNotClaimTheWholeAutomationPolicyIsInvalid() {
        SchedulingProperties properties = new SchedulingProperties();
        properties.setEnabled(true);
        properties.setRecurrenceRecordLimit(0);
        AutomationScheduleStore store = mock(AutomationScheduleStore.class);
        when(store.schedulingReadiness(any())).thenReturn(AutomationSchedulingReadiness.READY);

        var health = indicator(properties, store, mock(WorkRecurrenceControlStore.class)).health();

        assertEquals("DOWN", health.getStatus().getCode());
        assertEquals("RECURRENCE_NOT_READY", health.getDetails().get("state"));
        assertEquals("CONFIG_INVALID", health.getDetails().get("recurrenceState"));
    }

    @Test
    void disabledRecurrenceIsExplicitWithoutClaimingAReadyRecurrenceLane() {
        SchedulingProperties properties = new SchedulingProperties();
        properties.setEnabled(true);
        AutomationScheduleStore store = mock(AutomationScheduleStore.class);
        WorkRecurrenceControlStore recurrenceControl = mock(WorkRecurrenceControlStore.class);
        when(store.schedulingReadiness(any())).thenReturn(AutomationSchedulingReadiness.READY);
        when(recurrenceControl.readiness(any())).thenReturn(WorkRecurrenceReadiness.DISABLED);

        var health = indicator(properties, store, recurrenceControl).health();

        assertEquals("UP", health.getStatus().getCode());
        assertEquals("RECURRENCE_DISABLED", health.getDetails().get("state"));
        assertEquals("DISABLED", health.getDetails().get("recurrenceState"));
    }

    @Test
    void disabledRecurrenceShortCircuitsInvalidUnusedRecurrenceConfiguration() {
        SchedulingProperties properties = new SchedulingProperties();
        properties.setEnabled(true);
        properties.setRecurrenceRecordLimit(0);
        WorkRecurrenceProperties recurrenceProperties = new WorkRecurrenceProperties();
        recurrenceProperties.setEnabled(false);
        recurrenceProperties.setReconciliationDelay("not-a-duration");
        AutomationScheduleStore store = mock(AutomationScheduleStore.class);
        WorkRecurrenceControlStore recurrenceControl = mock(WorkRecurrenceControlStore.class);
        when(store.schedulingReadiness(any())).thenReturn(AutomationSchedulingReadiness.READY);

        var health = new AutomationSchedulingHealthIndicator(properties, store, recurrenceProperties, recurrenceControl).health();

        assertEquals("UP", health.getStatus().getCode());
        assertEquals("RECURRENCE_DISABLED", health.getDetails().get("state"));
        verifyNoInteractions(recurrenceControl);
    }

    private static AutomationSchedulingHealthIndicator indicator(SchedulingProperties properties,
                                                                  AutomationScheduleStore store,
                                                                  WorkRecurrenceControlStore recurrenceControl) {
        return new AutomationSchedulingHealthIndicator(properties, store, new WorkRecurrenceProperties(), recurrenceControl);
    }
}
