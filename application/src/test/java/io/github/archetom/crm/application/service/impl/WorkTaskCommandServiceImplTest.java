package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.WorkTaskAssigneeUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskBusinessRecordUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskFollowerUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskVersionRequest;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.WorkTask;
import io.github.archetom.crm.domain.model.WorkTaskPriority;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.WorkTaskBusinessRecord;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.WorkTaskRepository;
import io.github.archetom.crm.domain.repository.WorkTaskCommentRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkTaskCommandServiceImplTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId CREATOR = new MemberId(7L);
    private static final MemberId ASSIGNEE = new MemberId(8L);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 19, 12, 0);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC);

    private final WorkTaskRepository repository = mock(WorkTaskRepository.class);
    private final OrganizationMembershipRepository memberships =
            mock(OrganizationMembershipRepository.class);
    private final OrganizationTenantRepository tenants =
            mock(OrganizationTenantRepository.class);
    private final OrganizationAccessGuard accessGuard = mock(OrganizationAccessGuard.class);
    private final BusinessRecordAccessGuard recordAccessGuard =
            mock(BusinessRecordAccessGuard.class);
    private final WorkTaskCommentRepository comments =
            mock(WorkTaskCommentRepository.class);
    private WorkTaskCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        WorkTaskApplicationSupport support = new WorkTaskApplicationSupport();
        service = new WorkTaskCommandServiceImpl(repository, memberships, tenants,
                accessGuard, recordAccessGuard, support,
                new WorkTaskOutputSupport(recordAccessGuard, support), comments,
                new CommandServiceTemplate("test-app", new TestTransactionManager()), CLOCK);
        when(accessGuard.requireActiveMembership(any(),
                org.mockito.ArgumentMatchers.eq(AuthorityCatalog.WORK_TASKS_WRITE)))
                .thenReturn(TENANT);
        when(tenants.lockForTenantChanges(TENANT)).thenReturn(true);
        when(memberships.findByMember(org.mockito.ArgumentMatchers.eq(TENANT), any()))
                .thenAnswer(invocation -> Optional.of(OrganizationMembership.activate(
                        TENANT, invocation.getArgument(1), null)));
        when(repository.save(org.mockito.ArgumentMatchers.eq(TENANT), any()))
                .thenAnswer(invocation -> {
                    WorkTask task = invocation.getArgument(1);
                    if (task.getId() != null) {
                        task.onPersisted(task.getId(), task.getVersion() + 1, NOW, NOW);
                    }
                    return task;
                });
    }

    @Test
    void creatorCanEditButOrdinaryAssigneeCannotManageStructure() {
        WorkTask task = persistedTask();
        AuthenticatedCaller creator = caller(CREATOR, false);
        when(repository.findVisibleByIdForUpdate(TENANT, task.getId(), CREATOR, false))
                .thenReturn(Optional.of(task));

        var updated = service.update(creator, task.getId().getValue(),
                new WorkTaskUpdateRequest().setTitle("Changed")
                        .setPriority("HIGH").setVersion(task.getVersion()));
        assertTrue(updated.isSuccess());
        assertEquals("Changed", updated.getData().title());

        WorkTask other = persistedTask();
        AuthenticatedCaller assignee = caller(ASSIGNEE, false);
        when(repository.findVisibleByIdForUpdate(TENANT, other.getId(), ASSIGNEE, false))
                .thenReturn(Optional.of(other));
        var denied = service.update(assignee, other.getId().getValue(),
                new WorkTaskUpdateRequest().setTitle("Not allowed")
                        .setPriority("NORMAL").setVersion(other.getVersion()));
        assertFalse(denied.isSuccess());
    }

    @Test
    void currentAssigneeCanProgressAndOldVersionFailsClosed() {
        WorkTask task = persistedTask();
        AuthenticatedCaller assignee = caller(ASSIGNEE, false);
        when(repository.findVisibleByIdForUpdate(TENANT, task.getId(), ASSIGNEE, false))
                .thenReturn(Optional.of(task));

        var started = service.start(assignee, task.getId().getValue(),
                new WorkTaskVersionRequest().setVersion(0L));
        assertTrue(started.isSuccess());
        assertEquals("IN_PROGRESS", started.getData().status());

        var stale = service.complete(assignee, task.getId().getValue(),
                new WorkTaskVersionRequest().setVersion(0L));
        assertFalse(stale.isSuccess());
    }

    @Test
    void taskManagerStillNeedsWriteButCanCancelAnyVisibleTask() {
        WorkTask task = persistedTask();
        MemberId managerId = new MemberId(10L);
        AuthenticatedCaller manager = caller(managerId, true);
        when(repository.findVisibleByIdForUpdate(TENANT, task.getId(), managerId, true))
                .thenReturn(Optional.of(task));

        var cancelled = service.cancel(manager, task.getId().getValue(),
                new WorkTaskVersionRequest().setVersion(0L));
        assertTrue(cancelled.isSuccess());
        assertEquals("CANCELLED", cancelled.getData().status());
        verify(accessGuard).requireActiveMembership(
                manager, AuthorityCatalog.WORK_TASKS_WRITE);
    }

    @Test
    void invisibleIdsReturnSafeNotFoundWithoutSaving() {
        AuthenticatedCaller caller = caller(ASSIGNEE, false);
        when(repository.findVisibleByIdForUpdate(
                TENANT, new WorkTaskId(42L), ASSIGNEE, false))
                .thenReturn(Optional.empty());

        var result = service.complete(caller, 42L,
                new WorkTaskVersionRequest().setVersion(0L));

        assertFalse(result.isSuccess());
        verify(repository, never()).save(any(), any());
    }

    @Test
    void reassignmentRejectsInvalidVersionBeforeLockingOrLoadingTask() {
        AuthenticatedCaller creator = caller(CREATOR, false);

        var missingVersion = service.updateAssignee(creator, 31L,
                new WorkTaskAssigneeUpdateRequest()
                        .setAssigneeActorId(ASSIGNEE.getValue()));
        var negativeVersion = service.updateAssignee(creator, 31L,
                new WorkTaskAssigneeUpdateRequest()
                        .setAssigneeActorId(ASSIGNEE.getValue())
                        .setVersion(-1L));

        assertParameterInvalid(missingVersion);
        assertParameterInvalid(negativeVersion);
        verifyNoInteractions(tenants, repository);
    }

    @Test
    void creatorReplacesUniqueActiveFollowersWithoutChangingProjectionVersion() {
        WorkTask task = persistedTask();
        AuthenticatedCaller creator = caller(CREATOR, false);
        MemberId follower = new MemberId(11L);
        when(repository.findVisibleByIdForUpdate(TENANT, task.getId(), CREATOR, false))
                .thenReturn(Optional.of(task));

        var result = service.replaceFollowers(creator, task.getId().getValue(),
                new WorkTaskFollowerUpdateRequest()
                        .setFollowerActorIds(java.util.List.of(follower.getValue()))
                        .setVersion(task.getVersion()));

        assertTrue(result.isSuccess());
        assertEquals(java.util.List.of(follower.getValue()),
                result.getData().followerActorIds());
        assertEquals(0L, result.getData().worklistProjectionVersion());
    }

    @ParameterizedTest
    @EnumSource(value = BusinessObjectType.class, names = {
            "CUSTOMER", "CONTACT", "LEAD", "OPPORTUNITY",
            "CONTRACT", "INVOICE", "RECEIVABLE", "RECEIVABLE_PLAN"})
    void everySupportedBusinessTypeRechecksOwningWriteScope(BusinessObjectType type) {
        WorkTask task = persistedTask();
        AuthenticatedCaller creator = caller(CREATOR, false);
        when(repository.findVisibleByIdForUpdate(TENANT, task.getId(), CREATOR, false))
                .thenReturn(Optional.of(task));
        when(recordAccessGuard.requireVisible(creator, type, 41L, true))
                .thenReturn(TENANT);
        when(recordAccessGuard.visibleSubjectIds(creator, TENANT, type, Set.of(41L)))
                .thenReturn(Set.of(41L));

        var result = service.updateBusinessRecord(creator, task.getId().getValue(),
                new WorkTaskBusinessRecordUpdateRequest()
                        .setBusinessObjectType(type.name())
                        .setBusinessRecordId(41L)
                        .setVersion(task.getVersion()));

        assertTrue(result.isSuccess());
        assertEquals(type.name(), result.getData().businessObjectType());
        verify(recordAccessGuard).requireVisible(creator, type, 41L, true);
    }

    @Test
    void clearingBusinessRecordDoesNotProbeTheCurrentHiddenRelation() {
        WorkTask task = persistedTask();
        task.updateBusinessRecord(new WorkTaskBusinessRecord(
                BusinessObjectType.CUSTOMER, 40L), NOW);
        AuthenticatedCaller creator = caller(CREATOR, false);
        when(repository.findVisibleByIdForUpdate(TENANT, task.getId(), CREATOR, false))
                .thenReturn(Optional.of(task));

        var result = service.updateBusinessRecord(creator, task.getId().getValue(),
                new WorkTaskBusinessRecordUpdateRequest().setVersion(task.getVersion()));

        assertTrue(result.isSuccess());
        assertNull(result.getData().businessObjectType());
        verify(recordAccessGuard, never()).requireVisible(
                creator, BusinessObjectType.CUSTOMER, 40L, true);
    }

    @Test
    void replacingBusinessRecordChecksOnlyTheExplicitReplacementWriteScope() {
        WorkTask task = persistedTask();
        task.updateBusinessRecord(new WorkTaskBusinessRecord(
                BusinessObjectType.CUSTOMER, 40L), NOW);
        AuthenticatedCaller creator = caller(CREATOR, false);
        when(repository.findVisibleByIdForUpdate(TENANT, task.getId(), CREATOR, false))
                .thenReturn(Optional.of(task));
        when(recordAccessGuard.requireVisible(
                creator, BusinessObjectType.CONTACT, 41L, true)).thenReturn(TENANT);
        when(recordAccessGuard.visibleSubjectIds(
                creator, TENANT, BusinessObjectType.CONTACT, Set.of(41L)))
                .thenReturn(Set.of(41L));

        var result = service.updateBusinessRecord(creator, task.getId().getValue(),
                new WorkTaskBusinessRecordUpdateRequest()
                        .setBusinessObjectType("CONTACT")
                        .setBusinessRecordId(41L)
                        .setVersion(task.getVersion()));

        assertTrue(result.isSuccess());
        verify(recordAccessGuard, never()).requireVisible(
                creator, BusinessObjectType.CUSTOMER, 40L, true);
        verify(recordAccessGuard).requireVisible(
                creator, BusinessObjectType.CONTACT, 41L, true);
    }

    private WorkTask persistedTask() {
        WorkTask task = WorkTask.create(TENANT, CREATOR, ASSIGNEE,
                "Task", null, WorkTaskPriority.NORMAL, NOW.plusDays(1), NOW);
        task.onPersisted(new WorkTaskId(31L), 0L, NOW, NOW);
        return task;
    }

    private AuthenticatedCaller caller(MemberId memberId, boolean manager) {
        Set<String> authorities = manager
                ? Set.of(AuthorityCatalog.WORK_TASKS_WRITE,
                        AuthorityCatalog.WORK_TASKS_MANAGE)
                : Set.of(AuthorityCatalog.WORK_TASKS_WRITE);
        return new AuthenticatedCaller(memberId.getValue(), TENANT.getValue(), authorities);
    }

    private void assertParameterInvalid(io.github.archetom.common.result.Result<?> result) {
        assertFalse(result.isSuccess());
        assertEquals("101", result.getErrorContext().fetchRootError()
                .getErrorCode().getErrorSpecific());
    }
}
