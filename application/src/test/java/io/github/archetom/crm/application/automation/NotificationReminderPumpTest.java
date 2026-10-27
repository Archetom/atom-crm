package io.github.archetom.crm.application.automation;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.application.properties.NotificationReminderProperties;
import io.github.archetom.crm.application.properties.NotificationDeliveryProperties;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryStore;
import io.github.archetom.crm.application.port.out.notification.NotificationPreferenceStore;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.domain.repository.NotificationRepository;
import io.github.archetom.crm.domain.repository.WorkTaskReminderRepository;
import io.github.archetom.crm.domain.repository.WorkTaskRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationReminderPumpTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void claimFailureEscapesThePumpInsteadOfBeingReportedAsSuccess() {
        NotificationReminderProperties properties = new NotificationReminderProperties();
        CommandServiceTemplate commands = mock(CommandServiceTemplate.class);
        Result<Void> success = new Result<>();
        success.setSuccess(true);
        Result<?> failure = new Result<>();
        failure.setSuccess(false);
        when(commands.execute(eq(io.github.archetom.crm.application.operation.UseCaseOperation.WORK_TASK_REMINDER_EFFECT),
                any(), any())).thenReturn((Result) success, (Result) success, (Result) failure);

        NotificationReminderPump pump = new NotificationReminderPump(
                mock(WorkTaskReminderRepository.class), mock(NotificationRepository.class),
                mock(NotificationDeliveryStore.class), mock(NotificationPreferenceStore.class),
                new NotificationDeliveryProperties(),
                mock(WorkTaskRepository.class), mock(TenantAuthorizationEvaluator.class), properties, commands,
                mock(BackgroundWorkObservability.class));

        assertThrows(IllegalStateException.class, pump::runOnce);
    }
}
