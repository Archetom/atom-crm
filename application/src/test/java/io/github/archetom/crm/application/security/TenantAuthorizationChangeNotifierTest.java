package io.github.archetom.crm.application.security;

import io.github.archetom.crm.application.transaction.AfterCommitExecutor;
import io.github.archetom.crm.domain.event.DomainEventPublisher;
import io.github.archetom.crm.domain.event.TenantAuthorizationChangedEvent;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TenantAuthorizationChangeNotifierTest {

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void cacheAndEventActionsRemainInvisibleUntilCommit() {
        TenantAuthorityCache cache = mock(TenantAuthorityCache.class);
        DomainEventPublisher publisher = mock(DomainEventPublisher.class);
        TenantId tenantId = new TenantId(9L);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        TenantAuthorizationChangeNotifier notifier = new TenantAuthorizationChangeNotifier(
                new AfterCommitExecutor(), cache, publisher);

        notifier.changed(tenantId);

        verify(cache, never()).invalidateTenant(tenantId);
        verify(publisher, never()).publish(any());
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        assertEquals(2, synchronizations.size());
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        verify(cache).invalidateTenant(tenantId);
        verify(publisher).publish(any(TenantAuthorizationChangedEvent.class));
    }
}
