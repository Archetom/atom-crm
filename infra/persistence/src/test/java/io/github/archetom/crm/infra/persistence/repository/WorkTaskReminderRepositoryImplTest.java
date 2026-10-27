package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.infra.persistence.mysql.mapper.WorkTaskReminderOccurrenceMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskReminderOccurrencePO;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkTaskReminderRepositoryImplTest {

    @Test
    void exhaustedOccurrenceBecomesDeadInsteadOfBeingLeasedAgain() {
        WorkTaskReminderOccurrenceMapper mapper = mock(WorkTaskReminderOccurrenceMapper.class);
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 10, 0);
        WorkTaskReminderOccurrencePO exhausted = row(now);
        exhausted.setAttemptCount(5);
        when(mapper.selectClaimableForUpdate(eq(now), eq(5))).thenReturn(exhausted, null);
        when(mapper.deadBeforeClaim(7L, now, 5)).thenReturn(1);

        var claimed = new WorkTaskReminderRepositoryImpl(mapper)
                .claimDue(UUID.randomUUID(), now, Duration.ofMinutes(2), 5);

        assertTrue(claimed.occurrence().isEmpty());
        assertTrue(claimed.deadCount() == 1);
        verify(mapper).deadBeforeClaim(7L, now, 5);
    }

    private static WorkTaskReminderOccurrencePO row(LocalDateTime now) {
        WorkTaskReminderOccurrencePO value = new WorkTaskReminderOccurrencePO();
        value.setId(7L); value.setTenantId(1L); value.setWorkTaskId(2L);
        value.setNotificationProjectionVersion(0L); value.setStepKey("AT_DUE_0_ASSIGNEE");
        value.setDueTime(now.minusMinutes(1)); value.setStatus("READY"); value.setAttemptCount(0);
        value.setAvailableAt(now.minusMinutes(1)); value.setDeadlineAt(now.plusHours(1));
        value.setLeaseFence(0L); value.setCreatedTime(now.minusMinutes(1));
        return value;
    }
}
