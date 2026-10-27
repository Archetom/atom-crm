package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryClaim;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryOutcomeCounts;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryStatus;
import io.github.archetom.crm.application.port.out.notification.NotificationDispatchOutcome;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.NotificationDeliveryMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.NotificationDeliveryPO;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDeliveryStoreRepositoryImplTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 22, 12, 0);

    @Test
    void reusesOnlyAnExpiredIdempotentDispatchInsideItsOriginalWindow() {
        NotificationDeliveryMapper mapper = mock(NotificationDeliveryMapper.class);
        NotificationDeliveryPO dispatching = expiredDispatch("IDEMPOTENT_SEND", NOW.plusMinutes(1));
        when(mapper.selectClaimableForUpdate(eq(NOW), eq(5), any())).thenReturn(dispatching, null);
        when(mapper.recoverExpiredIdempotentDispatch(9L, NOW, 5)).thenReturn(1);

        NotificationDeliveryClaim claim = new NotificationDeliveryStoreRepositoryImpl(mapper)
                .claimDue(UUID.randomUUID(), NOW, Duration.ofMinutes(2), 5);

        assertEquals(0, claim.ambiguousCount());
        assertEquals(0, claim.deadCount());
        assertEquals(java.util.Optional.empty(), claim.delivery());
        verify(mapper).recoverExpiredIdempotentDispatch(9L, NOW, 5);
    }

    @Test
    void turnsAnExpiredAtMostOnceDispatchIntoAmbiguousWithoutRetryingIt() {
        NotificationDeliveryMapper mapper = mock(NotificationDeliveryMapper.class);
        NotificationDeliveryPO dispatching = expiredDispatch("AT_MOST_ONCE_SEND", null);
        when(mapper.selectClaimableForUpdate(eq(NOW), eq(5), any())).thenReturn(dispatching, null);
        when(mapper.markExpiredDispatchAmbiguous(9L, NOW)).thenReturn(1);

        NotificationDeliveryClaim claim = new NotificationDeliveryStoreRepositoryImpl(mapper)
                .claimDue(UUID.randomUUID(), NOW, Duration.ofMinutes(2), 5);

        assertEquals(1, claim.ambiguousCount());
        assertEquals(java.util.Optional.empty(), claim.delivery());
        verify(mapper).markExpiredDispatchAmbiguous(9L, NOW);
    }

    @Test
    void retriesAnAmbiguousIdempotentDispatchOnlyThroughItsPersistedProtocol() {
        NotificationDeliveryMapper mapper = mock(NotificationDeliveryMapper.class);
        NotificationDeliveryPO settled = row("RETRY_WAIT");
        when(mapper.retryIdempotentOrTerminal(eq(1L), eq(9L), any(), eq(3L), eq(NOW), eq(5),
                eq(NOW.plusMinutes(1)), eq("AMBIGUOUS"))).thenReturn(1);
        when(mapper.selectByTenantId(1L, 9L)).thenReturn(settled);

        var result = new NotificationDeliveryStoreRepositoryImpl(mapper).settleDispatch(new TenantId(1L), 9L,
                UUID.randomUUID(), 3L, NotificationDispatchOutcome.AMBIGUOUS, NOW, 5, Duration.ofMinutes(1));

        assertEquals(java.util.Optional.of(NotificationDeliveryStatus.RETRY_WAIT), result);
        verify(mapper).retryIdempotentOrTerminal(eq(1L), eq(9L), any(), eq(3L), eq(NOW), eq(5),
                eq(NOW.plusMinutes(1)), eq("AMBIGUOUS"));
        verify(mapper, never()).settleAtMostOnceAmbiguous(anyLong(), anyLong(), anyString(), anyLong(), anyString(), any());
        verify(mapper, never()).selectById(anyLong());
    }

    @Test
    void neverAutomaticallyRetriesAnAtMostOnceDispatchFailure() {
        NotificationDeliveryMapper mapper = mock(NotificationDeliveryMapper.class);
        NotificationDeliveryPO settled = row("AMBIGUOUS");
        when(mapper.settleAtMostOnceAmbiguous(eq(1L), eq(9L), any(), eq(3L), eq("AT_MOST_ONCE_FAILURE"), eq(NOW)))
                .thenReturn(1);
        when(mapper.selectByTenantId(1L, 9L)).thenReturn(settled);

        var result = new NotificationDeliveryStoreRepositoryImpl(mapper).settleDispatch(new TenantId(1L), 9L,
                UUID.randomUUID(), 3L, NotificationDispatchOutcome.RETRYABLE_FAILURE, NOW, 5,
                Duration.ofMinutes(1));

        assertEquals(java.util.Optional.of(NotificationDeliveryStatus.AMBIGUOUS), result);
        verify(mapper).settleAtMostOnceAmbiguous(eq(1L), eq(9L), any(), eq(3L),
                eq("AT_MOST_ONCE_FAILURE"), eq(NOW));
        verify(mapper, never()).selectById(anyLong());
    }

    @Test
    void returnsAmbiguousWhenSuppressingAClaimWithDispatchHistory() {
        NotificationDeliveryMapper mapper = mock(NotificationDeliveryMapper.class);
        NotificationDeliveryPO settled = row("AMBIGUOUS");
        when(mapper.suppressClaim(eq(1L), eq(9L), any(), eq(3L), eq("PREFERENCE_OFF"), eq(NOW))).thenReturn(1);
        when(mapper.selectByTenantId(1L, 9L)).thenReturn(settled);

        NotificationDeliveryStatus status = new NotificationDeliveryStoreRepositoryImpl(mapper)
                .suppressClaim(new TenantId(1L), 9L, UUID.randomUUID(), 3L, "PREFERENCE_OFF", NOW);

        assertEquals(NotificationDeliveryStatus.AMBIGUOUS, status);
        verify(mapper, never()).selectById(anyLong());
    }

    @Test
    void deadLettersAnExpiredLeaseInsteadOfRequeueingAtTheDeadline() {
        NotificationDeliveryMapper mapper = mock(NotificationDeliveryMapper.class);
        NotificationDeliveryPO lease = new NotificationDeliveryPO();
        lease.setId(9L); lease.setStatus("LEASED"); lease.setAttemptCount(1); lease.setDeadlineAt(NOW);
        lease.setEffectCertainty("NO_EFFECT");
        when(mapper.selectClaimableForUpdate(eq(NOW), eq(5), any())).thenReturn(lease, null);
        when(mapper.deadBeforeClaim(9L, NOW, 5)).thenReturn(1);

        NotificationDeliveryClaim claim = new NotificationDeliveryStoreRepositoryImpl(mapper)
                .claimDue(UUID.randomUUID(), NOW, Duration.ofMinutes(2), 5);

        assertEquals(1, claim.deadCount());
        verify(mapper).deadBeforeClaim(9L, NOW, 5);
        verify(mapper, never()).requeueExpiredLease(anyLong(), any());
    }

    @Test
    void neverReclassifiesAPreviouslyAmbiguousRetryAsDeadAtTheDeadline() {
        NotificationDeliveryMapper mapper = mock(NotificationDeliveryMapper.class);
        NotificationDeliveryPO retry = new NotificationDeliveryPO();
        retry.setId(9L); retry.setStatus("RETRY_WAIT"); retry.setAttemptCount(1); retry.setDeadlineAt(NOW);
        retry.setEffectCertainty("AMBIGUOUS");
        when(mapper.selectClaimableForUpdate(eq(NOW), eq(5), any())).thenReturn(retry, null);
        when(mapper.deadBeforeClaim(9L, NOW, 5)).thenReturn(1);

        NotificationDeliveryClaim claim = new NotificationDeliveryStoreRepositoryImpl(mapper)
                .claimDue(UUID.randomUUID(), NOW, Duration.ofMinutes(2), 5);

        assertEquals(1, claim.ambiguousCount());
        assertEquals(0, claim.deadCount());
    }

    @Test
    void exposesDeadlineTransitionsByTheirCommittedOutcome() {
        NotificationDeliveryMapper mapper = mock(NotificationDeliveryMapper.class);
        when(mapper.expireOverdueNoEffect(NOW, 100)).thenReturn(2);
        when(mapper.expireOverdueAmbiguousEffect(NOW, 100)).thenReturn(3);
        when(mapper.expireOverdueDispatch(NOW, 100)).thenReturn(4);

        NotificationDeliveryOutcomeCounts outcomes = new NotificationDeliveryStoreRepositoryImpl(mapper)
                .expireOverdue(NOW, 100);

        assertEquals(new NotificationDeliveryOutcomeCounts(0, 7, 2), outcomes);
    }

    private static NotificationDeliveryPO row(String status) {
        NotificationDeliveryPO value = new NotificationDeliveryPO();
        value.setId(9L); value.setStatus(status); value.setEffectCertainty("NO_EFFECT");
        return value;
    }

    private static NotificationDeliveryPO expiredDispatch(String guarantee, LocalDateTime idempotencyUntil) {
        NotificationDeliveryPO row = new NotificationDeliveryPO();
        row.setId(9L); row.setStatus("DISPATCHING"); row.setGuarantee(guarantee);
        row.setIdempotencyUntil(idempotencyUntil); row.setDeadlineAt(NOW.plusDays(1)); row.setAttemptCount(1);
        row.setEffectCertainty("AMBIGUOUS");
        return row;
    }
}
