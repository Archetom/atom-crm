package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskQueryRequest;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.WorkTaskRepository;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkTaskQueryServiceImplTest {

    private final WorkTaskRepository repository = mock(WorkTaskRepository.class);
    private final OrganizationAccessGuard accessGuard = mock(OrganizationAccessGuard.class);
    private final BusinessRecordAccessGuard recordAccessGuard =
            mock(BusinessRecordAccessGuard.class);
    private final WorkTaskApplicationSupport support = new WorkTaskApplicationSupport();
    private final WorkTaskQueryServiceImpl service = new WorkTaskQueryServiceImpl(
            repository, accessGuard, recordAccessGuard, support,
            new WorkTaskOutputSupport(recordAccessGuard, support),
            new QueryServiceTemplate("test-app", new TestTransactionManager()),
            Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void rejectsPageBeyondStandardLimitBeforeAuthorizationOrRepositoryAccess() {
        WorkTaskQueryRequest request = new WorkTaskQueryRequest();
        request.setPage(QueryRequest.MAX_PAGE + 1);

        Result<?> result = service.query(caller(), request);

        assertFalse(result.isSuccess());
        assertEquals("101", result.getErrorContext().fetchRootError()
                .getErrorCode().getErrorSpecific());
        verifyNoInteractions(accessGuard, repository);
    }

    @Test
    void relationshipReadCheckAndTaskQueryShareTheReadSnapshot() {
        TestTransactionManager transactions = new TestTransactionManager();
        TenantId tenantId = new TenantId(9L);
        AuthenticatedCaller caller = caller();
        when(accessGuard.requireActiveMembership(caller, AuthorityCatalog.WORK_TASKS_READ))
                .thenReturn(tenantId);
        when(recordAccessGuard.requireVisible(
                caller, BusinessObjectType.CUSTOMER, 41L, false))
                .thenAnswer(invocation -> {
                    assertTrue(transactions.active());
                    return tenantId;
                });
        when(repository.findPage(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    assertTrue(transactions.active());
                    return new PageResult<>(1, 20, 0, List.of());
                });
        WorkTaskQueryServiceImpl transactionalService = new WorkTaskQueryServiceImpl(
                repository, accessGuard, recordAccessGuard, support,
                new WorkTaskOutputSupport(recordAccessGuard, support),
                new QueryServiceTemplate("test-app", transactions),
                Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC));

        Result<?> result = transactionalService.query(caller,
                new WorkTaskQueryRequest().setBusinessObjectType("CUSTOMER")
                        .setBusinessRecordId(41L));

        assertTrue(result.isSuccess());
        assertFalse(transactions.active());
        // The snapshot assertion above lives inside the stub answer, so it only runs if the
        // relationship check is actually reached. Without this the guard could be dropped from
        // the use case entirely and the test would still pass on the repository answer alone.
        verify(recordAccessGuard).requireVisible(caller, BusinessObjectType.CUSTOMER, 41L, false);
    }

    private AuthenticatedCaller caller() {
        return new AuthenticatedCaller(7L, 9L, Set.of(AuthorityCatalog.WORK_TASKS_READ));
    }
}
