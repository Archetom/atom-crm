package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.LeadIntakeClientDeactivateRequest;
import io.github.archetom.crm.application.port.out.LeadIntakeRateLimitInvalidator;
import io.github.archetom.crm.application.security.LeadAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.transaction.AfterCommitExecutor;
import io.github.archetom.crm.domain.entity.LeadIntakeClient;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.LeadIntakeClientRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.valueobject.LeadIntakeClientId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.common.result.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadIntakeClientServiceImplTest {

    private static final TenantId TENANT_ID = new TenantId(99L);
    private static final LeadIntakeClientId CLIENT_ID = new LeadIntakeClientId(10L);
    private static final String KEY_ID = "intake-key";

    @Mock
    private LeadIntakeClientRepository repository;
    @Mock
    private OrganizationMembershipRepository membershipRepository;
    @Mock
    private OrganizationAccessGuard accessGuard;
    @Mock
    private LeadAccessResolver leadAccessResolver;
    @Mock
    private LeadApplicationSupport leadSupport;
    @Mock
    private LeadIntakeRateLimitInvalidator rateLimitInvalidator;
    @Mock
    private PlatformTransactionManager transactionManager;

    private LeadIntakeClientServiceImpl service;
    private LeadIntakeClient client;

    @BeforeEach
    void setUp() {
        client = activeClient();
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(accessGuard.requireActiveMembership(any(), eq(AuthorityCatalog.LEADS_INTAKE_MANAGE)))
                .thenReturn(TENANT_ID);
        when(repository.findById(TENANT_ID, CLIENT_ID)).thenReturn(Optional.of(client));
        when(repository.save(TENANT_ID, client)).thenReturn(client);
        service = new LeadIntakeClientServiceImpl(repository, membershipRepository, accessGuard,
                leadAccessResolver, leadSupport, new CommandServiceTemplate("test-app", transactionManager),
                new QueryServiceTemplate("test-app", transactionManager), new AfterCommitExecutor(),
                rateLimitInvalidator);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void deactivationInvalidatesRateLimitStateOnlyAfterCommit() {
        beginTransactionSynchronization();

        Result<?> result = service.deactivate(caller(), CLIENT_ID.getValue(), deactivateRequest());

        assertTrue(result.isSuccess());
        verify(rateLimitInvalidator, never()).invalidate(any(), any());
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        verify(rateLimitInvalidator).invalidate(TENANT_ID, KEY_ID);
    }

    @Test
    void rolledBackDeactivationDoesNotInvalidateRateLimitState() {
        beginTransactionSynchronization();

        Result<?> result = service.deactivate(caller(), CLIENT_ID.getValue(), deactivateRequest());

        assertTrue(result.isSuccess());
        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(rateLimitInvalidator, never()).invalidate(any(), any());
    }

    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private AuthenticatedCaller caller() {
        return new AuthenticatedCaller(7L, TENANT_ID.getValue(), Set.of(AuthorityCatalog.LEADS_INTAKE_MANAGE));
    }

    private LeadIntakeClientDeactivateRequest deactivateRequest() {
        return new LeadIntakeClientDeactivateRequest().setVersion(client.getVersion());
    }

    private LeadIntakeClient activeClient() {
        LocalDateTime now = LocalDateTime.now();
        return LeadIntakeClient.reconstitute(new LeadIntakeClient.Snapshot(
                CLIENT_ID, TENANT_ID, new MemberId(7L), KEY_ID, "a".repeat(64), true,
                3L, now, now));
    }
}
