package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.WorklistAcknowledgementRequest;
import io.github.archetom.crm.api.dto.request.WorklistItemAcknowledgementRequest;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.WorklistQueryService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.application.vo.WorklistItemVO;
import io.github.archetom.crm.application.vo.WorklistPageVO;
import io.github.archetom.crm.domain.model.WorklistItemFingerprint;
import io.github.archetom.crm.domain.repository.WorklistAcknowledgementRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorklistAcknowledgementServiceImplTest {
    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId MEMBER = new MemberId(7L);
    private static final AuthenticatedCaller CALLER = new AuthenticatedCaller(7L, 9L,
            Set.of("worklist:read"));
    private static final LocalDate DAY = LocalDate.of(2026, 7, 18);
    private static final LocalDateTime DUE = DAY.atTime(9, 0);

    @Test
    void exactAcknowledgementIsIdempotentAndANewVersionReappears() {
        OrganizationAccessGuard guard = mock(OrganizationAccessGuard.class);
        WorklistQueryService queries = mock(WorklistQueryService.class);
        WorklistAcknowledgementRepository repository = mock(WorklistAcknowledgementRepository.class);
        when(guard.requireActiveMembership(CALLER, "worklist:read")).thenReturn(TENANT);
        AtomicReference<WorklistItemVO> visible = new AtomicReference<>(item(1L));
        when(queries.query(eq(CALLER), eq(DAY), any(), eq(100)))
                .thenAnswer(invocation -> page(visible.get()));
        WorklistItemFingerprint first = fingerprint(1L);
        when(repository.findAcknowledged(TENANT, MEMBER, Set.of(first)))
                .thenReturn(Set.of(), Set.of(first));
        WorklistAcknowledgementServiceImpl service = new WorklistAcknowledgementServiceImpl(
                guard, queries, repository,
                new CommandServiceTemplate("test-app", new TestTransactionManager()));

        assertTrue(service.acknowledge(CALLER, request(1L)).isSuccess());
        assertTrue(service.acknowledge(CALLER, request(1L)).isSuccess());

        visible.set(item(2L));
        when(repository.findAcknowledged(TENANT, MEMBER, Set.of(fingerprint(2L))))
                .thenReturn(Set.of());
        assertTrue(service.acknowledge(CALLER, request(2L)).isSuccess());

        verify(queries, times(2)).query(eq(CALLER), eq(DAY), any(), eq(100));
        verify(repository).saveAll(TENANT, MEMBER, List.of(first));
        verify(repository).saveAll(TENANT, MEMBER, List.of(fingerprint(2L)));
    }

    @Test
    void categoryAcknowledgementFailsClosedWhenTheBoundedScanIsIncomplete() {
        OrganizationAccessGuard guard = mock(OrganizationAccessGuard.class);
        WorklistQueryService queries = mock(WorklistQueryService.class);
        WorklistAcknowledgementRepository repository = mock(WorklistAcknowledgementRepository.class);
        when(guard.requireActiveMembership(CALLER, "worklist:read")).thenReturn(TENANT);
        AtomicInteger pages = new AtomicInteger();
        when(queries.query(eq(CALLER), eq(DAY), any(), eq(100)))
                .thenAnswer(invocation -> continuationPage(pages.incrementAndGet()));
        WorklistAcknowledgementServiceImpl service = new WorklistAcknowledgementServiceImpl(
                guard, queries, repository,
                new CommandServiceTemplate("test-app", new TestTransactionManager()));

        Result<Void> result = service.acknowledge(CALLER,
                new WorklistAcknowledgementRequest().setDate(DAY).setKind("WORK_TASK"));

        assertFalse(result.isSuccess());
        verify(queries, times(100)).query(eq(CALLER), eq(DAY), any(), eq(100));
        verify(repository, never()).saveAll(any(), any(), any());
    }

    private WorklistAcknowledgementRequest request(long version) {
        return new WorklistAcknowledgementRequest().setDate(DAY).setItems(List.of(
                new WorklistItemAcknowledgementRequest().setKind("FOLLOW_UP").setSourceId(42L)
                        .setSourceVersion(version).setDueTime(DUE)));
    }

    private Result<WorklistPageVO> page(WorklistItemVO item) {
        Result<WorklistPageVO> result = new Result<>();
        result.setSuccess(true);
        result.setData(new WorklistPageVO(List.of(item), null));
        return result;
    }

    private Result<WorklistPageVO> continuationPage(int page) {
        Result<WorklistPageVO> result = new Result<>();
        result.setSuccess(true);
        result.setData(new WorklistPageVO(List.of(), "cursor-" + page));
        return result;
    }

    private WorklistItemVO item(long version) {
        return new WorklistItemVO("FOLLOW_UP", 42L, version, "CUSTOMER", 12L,
                DUE, "Follow up");
    }

    private WorklistItemFingerprint fingerprint(long version) {
        return new WorklistItemFingerprint("FOLLOW_UP", 42L, version, DUE);
    }
}
