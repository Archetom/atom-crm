package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.ReturnVisitAccessResolver;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.domain.entity.Activity;
import io.github.archetom.crm.domain.entity.ReturnVisit;
import io.github.archetom.crm.domain.model.ActivityKind;
import io.github.archetom.crm.domain.model.ActivityStatus;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.ReturnVisitAccessMode;
import io.github.archetom.crm.domain.model.ReturnVisitStatus;
import io.github.archetom.crm.domain.model.ReturnVisitVisibility;
import io.github.archetom.crm.domain.model.WorklistSource;
import io.github.archetom.crm.domain.model.WorklistSourceRecord;
import io.github.archetom.crm.domain.model.WorklistItemFingerprint;
import io.github.archetom.crm.domain.model.WorkTaskWorklistRecord;
import io.github.archetom.crm.domain.policy.ReturnVisitFieldCatalog;
import io.github.archetom.crm.domain.repository.ActivityRepository;
import io.github.archetom.crm.domain.repository.ReturnVisitRepository;
import io.github.archetom.crm.domain.repository.WorklistAcknowledgementRepository;
import io.github.archetom.crm.domain.repository.WorklistSourceRepository;
import io.github.archetom.crm.domain.repository.WorkTaskRepository;
import io.github.archetom.crm.domain.valueobject.ActivityId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ReturnVisitId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorklistQueryServiceImplTest {
    private static final TenantId TENANT = new TenantId(9L);
    private static final AuthenticatedCaller ACTIVITY_CALLER = new AuthenticatedCaller(7L, 9L,
            Set.of("worklist:read", "activities:read"));
    private static final AuthenticatedCaller ALL_SOURCES_CALLER = new AuthenticatedCaller(7L, 9L,
            Set.of("worklist:read", "activities:read", "return-visits:read"));

    @Test
    void boundsCandidateScanAndReturnsContinuationCursor() {
        OrganizationAccessGuard organizationGuard = mock(OrganizationAccessGuard.class);
        BusinessRecordAccessGuard recordGuard = mock(BusinessRecordAccessGuard.class);
        ActivityRepository activities = mock(ActivityRepository.class);
        when(organizationGuard.requireActiveMembership(ACTIVITY_CALLER, "worklist:read"))
                .thenReturn(TENANT);
        AtomicInteger calls = new AtomicInteger();
        when(activities.findDue(eq(TENANT), any(), any(), any(), any(), any(Integer.class)))
                .thenAnswer(invocation -> {
                    int page = calls.getAndIncrement();
                    return page < 20 ? activities(page * 100L + 1L, 100)
                            : activities(2_001L, 1);
                });
        when(recordGuard.visibleSubjectIds(eq(ACTIVITY_CALLER), eq(TENANT),
                eq(BusinessObjectType.CUSTOMER), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.Collection<Long> ids = invocation.getArgument(3);
            return ids.contains(2_001L) ? Set.of(2_001L) : Set.of();
        });
        WorklistQueryServiceImpl service = service(organizationGuard, recordGuard, activities);

        var result = service.query(ACTIVITY_CALLER, LocalDate.of(2026, 7, 18), null, 50);

        assertTrue(result.isSuccess());
        assertTrue(result.getData().items().isEmpty());
        assertTrue(result.getData().nextCursor() != null);
        verify(activities, times(5)).findDue(eq(TENANT), any(), any(), any(), any(), eq(100));
        verify(recordGuard, times(5)).visibleSubjectIds(eq(ACTIVITY_CALLER), eq(TENANT),
                eq(BusinessObjectType.CUSTOMER), any());
    }

    @Test
    void rejectsLastSupportedDateBeforeComputingExclusiveBoundary() {
        OrganizationAccessGuard organizationGuard = mock(OrganizationAccessGuard.class);
        when(organizationGuard.requireActiveMembership(any(), eq("worklist:read"))).thenReturn(TENANT);
        WorklistQueryServiceImpl service = service(organizationGuard,
                mock(BusinessRecordAccessGuard.class), mock(ActivityRepository.class));

        var result = service.query(new AuthenticatedCaller(7L, 9L, Set.of("worklist:read")),
                LocalDate.of(9999, 12, 31), null, null);

        assertFalse(result.isSuccess());
    }

    @Test
    void independentlyAdvancesEachSourceAndPreservesGlobalOrderAcrossPages() {
        OrganizationAccessGuard organizationGuard = mock(OrganizationAccessGuard.class);
        BusinessRecordAccessGuard recordGuard = mock(BusinessRecordAccessGuard.class);
        ReturnVisitAccessResolver visitAccessResolver = mock(ReturnVisitAccessResolver.class);
        ReturnVisitApplicationSupport visitSupport = mock(ReturnVisitApplicationSupport.class);
        ActivityRepository activities = mock(ActivityRepository.class);
        ReturnVisitRepository visits = mock(ReturnVisitRepository.class);
        LocalDate day = LocalDate.of(2026, 7, 18);
        List<Activity> activityValues = List.of(activity(1L, day.atTime(9, 0)),
                activity(2L, day.atTime(9, 2)), activity(3L, day.atTime(9, 4)));
        List<ReturnVisit> visitValues = List.of(returnVisit(10L, day.atTime(9, 1)),
                returnVisit(11L, day.atTime(9, 3)), returnVisit(12L, day.atTime(9, 5)));
        ReturnVisitVisibility visibility = new ReturnVisitVisibility(DataScope.TENANT,
                new MemberId(7L), Set.of(), ReturnVisitAccessMode.READ);

        when(organizationGuard.requireActiveMembership(ALL_SOURCES_CALLER, "worklist:read"))
                .thenReturn(TENANT);
        when(recordGuard.visibleSubjectIds(eq(ALL_SOURCES_CALLER), eq(TENANT),
                eq(BusinessObjectType.CUSTOMER), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.Collection<Long> ids = invocation.getArgument(3);
            return Set.copyOf(ids);
        });
        when(activities.findDue(eq(TENANT), any(), any(), any(), any(), any(Integer.class)))
                .thenAnswer(invocation -> after(activityValues, invocation.getArgument(3),
                        invocation.getArgument(4), invocation.getArgument(5)));
        when(visitAccessResolver.access(ALL_SOURCES_CALLER, "return-visits:read",
                ReturnVisitAccessMode.READ)).thenReturn(new ReturnVisitAccessResolver.ReturnVisitAccess(
                TENANT, new MemberId(7L), visibility));
        when(visitSupport.accesses(TENANT, new MemberId(7L))).thenReturn(Map.of(
                new FieldCode(ReturnVisitFieldCatalog.NUMBER), EffectiveFieldAccess.owner(),
                new FieldCode(ReturnVisitFieldCatalog.VISIT_TIME), EffectiveFieldAccess.owner()));
        when(visits.findScheduled(eq(TENANT), eq(visibility), any(), any(), any(), any(), any(Integer.class)))
                .thenAnswer(invocation -> afterVisits(visitValues, invocation.getArgument(4),
                        invocation.getArgument(5), invocation.getArgument(6)));
        WorklistQueryServiceImpl service = new WorklistQueryServiceImpl(organizationGuard,
                recordGuard, null, visitAccessResolver, visitSupport, activities, visits, null,
                null, acknowledgements(),
                new QueryServiceTemplate("test-app", new TestTransactionManager()));

        var first = service.query(ALL_SOURCES_CALLER, day, null, 2);
        assertTrue(first.isSuccess());
        assertEquals(List.of("FOLLOW_UP:1", "RETURN_VISIT:10"), keys(first.getData().items()));
        assertNotNull(first.getData().nextCursor());

        var second = service.query(ALL_SOURCES_CALLER, day, first.getData().nextCursor(), 2);
        assertTrue(second.isSuccess());
        assertEquals(List.of("FOLLOW_UP:2", "RETURN_VISIT:11"), keys(second.getData().items()));
        assertNotNull(second.getData().nextCursor());

        var third = service.query(ALL_SOURCES_CALLER, day, second.getData().nextCursor(), 2);
        assertTrue(third.isSuccess());
        assertEquals(List.of("FOLLOW_UP:3", "RETURN_VISIT:12"), keys(third.getData().items()));
        assertNull(third.getData().nextCursor());
    }

    @Test
    void doesNotAdvancePastVisibleActivitiesExcludedByAnotherSource() {
        OrganizationAccessGuard organizationGuard = mock(OrganizationAccessGuard.class);
        BusinessRecordAccessGuard recordGuard = mock(BusinessRecordAccessGuard.class);
        ReturnVisitAccessResolver visitAccessResolver = mock(ReturnVisitAccessResolver.class);
        ReturnVisitApplicationSupport visitSupport = mock(ReturnVisitApplicationSupport.class);
        ActivityRepository activities = mock(ActivityRepository.class);
        ReturnVisitRepository visits = mock(ReturnVisitRepository.class);
        LocalDate day = LocalDate.of(2026, 7, 18);
        List<Activity> activityValues = List.of(activity(1L, day.atTime(9, 2)),
                activity(2L, day.atTime(9, 3)));
        List<ReturnVisit> visitValues = List.of(returnVisit(10L, day.atTime(9, 0)),
                returnVisit(11L, day.atTime(9, 1)));
        ReturnVisitVisibility visibility = new ReturnVisitVisibility(DataScope.TENANT,
                new MemberId(7L), Set.of(), ReturnVisitAccessMode.READ);
        when(organizationGuard.requireActiveMembership(ALL_SOURCES_CALLER, "worklist:read"))
                .thenReturn(TENANT);
        when(recordGuard.visibleSubjectIds(eq(ALL_SOURCES_CALLER), eq(TENANT),
                eq(BusinessObjectType.CUSTOMER), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.Collection<Long> ids = invocation.getArgument(3);
            return Set.copyOf(ids);
        });
        when(activities.findDue(eq(TENANT), any(), any(), any(), any(), any(Integer.class)))
                .thenAnswer(invocation -> after(activityValues, invocation.getArgument(3),
                        invocation.getArgument(4), invocation.getArgument(5)));
        when(visitAccessResolver.access(ALL_SOURCES_CALLER, "return-visits:read",
                ReturnVisitAccessMode.READ)).thenReturn(new ReturnVisitAccessResolver.ReturnVisitAccess(
                TENANT, new MemberId(7L), visibility));
        when(visitSupport.accesses(TENANT, new MemberId(7L))).thenReturn(Map.of(
                new FieldCode(ReturnVisitFieldCatalog.NUMBER), EffectiveFieldAccess.owner(),
                new FieldCode(ReturnVisitFieldCatalog.VISIT_TIME), EffectiveFieldAccess.owner()));
        when(visits.findScheduled(eq(TENANT), eq(visibility), any(), any(), any(), any(), any(Integer.class)))
                .thenAnswer(invocation -> afterVisits(visitValues, invocation.getArgument(4),
                        invocation.getArgument(5), invocation.getArgument(6)));
        WorklistQueryServiceImpl service = new WorklistQueryServiceImpl(organizationGuard,
                recordGuard, null, visitAccessResolver, visitSupport, activities, visits, null,
                null, acknowledgements(),
                new QueryServiceTemplate("test-app", new TestTransactionManager()));

        var first = service.query(ALL_SOURCES_CALLER, day, null, 2);
        assertEquals(List.of("RETURN_VISIT:10", "RETURN_VISIT:11"), keys(first.getData().items()));
        var second = service.query(ALL_SOURCES_CALLER, day, first.getData().nextCursor(), 2);
        assertEquals(List.of("FOLLOW_UP:1", "FOLLOW_UP:2"), keys(second.getData().items()));
        assertNull(second.getData().nextCursor());
    }

    @Test
    void includesFieldAuthorizedDirectSourcesAndSkipsOnlyAcknowledgedFingerprint() {
        OrganizationAccessGuard organizationGuard = mock(OrganizationAccessGuard.class);
        BusinessRecordAccessGuard recordGuard = mock(BusinessRecordAccessGuard.class);
        EffectiveFieldAccessResolver fields = mock(EffectiveFieldAccessResolver.class);
        WorklistSourceRepository sources = mock(WorklistSourceRepository.class);
        WorklistAcknowledgementRepository acknowledgements = mock(WorklistAcknowledgementRepository.class);
        AuthenticatedCaller caller = new AuthenticatedCaller(7L, 9L,
                Set.of("worklist:read", "contacts:read"));
        LocalDate day = LocalDate.of(2026, 7, 18);
        List<WorklistSourceRecord> values = List.of(
                new WorklistSourceRecord(WorklistSource.CONTACT, 1L, 1L,
                        BusinessObjectType.CONTACT, 1L, day.atTime(9, 0), "First"),
                new WorklistSourceRecord(WorklistSource.CONTACT, 2L, 1L,
                        BusinessObjectType.CONTACT, 2L, day.atTime(9, 1), "Second"));
        when(organizationGuard.requireActiveMembership(caller, "worklist:read")).thenReturn(TENANT);
        when(fields.resolve(TENANT, new MemberId(7L), BusinessObjectType.CONTACT)).thenReturn(Map.of(
                new FieldCode(io.github.archetom.crm.domain.policy.ContactFieldCatalog.NAME),
                EffectiveFieldAccess.owner(),
                new FieldCode(io.github.archetom.crm.domain.policy.ContactFieldCatalog.NEXT_CONTACT_TIME),
                EffectiveFieldAccess.owner()));
        when(sources.findDue(eq(TENANT), eq(new MemberId(7L)), eq(WorklistSource.CONTACT),
                any(), any(), any(), any(), any(Integer.class))).thenAnswer(invocation ->
                afterRecords(values, invocation.getArgument(5), invocation.getArgument(6),
                        invocation.getArgument(7)));
        when(recordGuard.visibleSubjectIds(eq(caller), eq(TENANT),
                eq(BusinessObjectType.CONTACT), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.Collection<Long> ids = invocation.getArgument(3);
            return Set.copyOf(ids);
        });
        WorklistItemFingerprint first = new WorklistItemFingerprint("CONTACT_FOLLOW_UP",
                1L, 1L, day.atTime(9, 0));
        when(acknowledgements.findAcknowledged(eq(TENANT), eq(new MemberId(7L)), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.Collection<WorklistItemFingerprint> requested = invocation.getArgument(2);
                    return requested.contains(first) ? Set.of(first) : Set.of();
                });
        WorklistQueryServiceImpl service = new WorklistQueryServiceImpl(organizationGuard,
                recordGuard, fields, null, null, mock(ActivityRepository.class), null, sources,
                null, acknowledgements,
                new QueryServiceTemplate("test-app", new TestTransactionManager()));

        var result = service.query(caller, day, null, 1);

        assertTrue(result.isSuccess());
        assertEquals(List.of("CONTACT_FOLLOW_UP:2"), keys(result.getData().items()));
    }

    @Test
    void includesOnlyReadAuthorizedAssigneeWorkTasksWithoutCrmVisibilityChecks() {
        OrganizationAccessGuard organizationGuard = mock(OrganizationAccessGuard.class);
        BusinessRecordAccessGuard recordGuard = mock(BusinessRecordAccessGuard.class);
        WorkTaskRepository workTasks = mock(WorkTaskRepository.class);
        AuthenticatedCaller caller = new AuthenticatedCaller(7L, 9L,
                Set.of("worklist:read", "work-tasks:read"));
        LocalDate day = LocalDate.of(2026, 7, 18);
        List<WorkTaskWorklistRecord> values = List.of(
                new WorkTaskWorklistRecord(new WorkTaskId(31L), 4L,
                        day.atTime(9, 0), "First task"),
                new WorkTaskWorklistRecord(new WorkTaskId(32L), 7L,
                        day.atTime(9, 1), "Second task"));
        when(organizationGuard.requireActiveMembership(caller, "worklist:read")).thenReturn(TENANT);
        when(workTasks.findDueForAssignee(eq(TENANT), eq(new MemberId(7L)), any(), any(),
                any(), any(), any(Integer.class))).thenAnswer(invocation -> afterWorkTasks(
                values, invocation.getArgument(4), invocation.getArgument(5),
                invocation.getArgument(6)));
        WorklistQueryServiceImpl service = new WorklistQueryServiceImpl(organizationGuard,
                recordGuard, null, null, null, mock(ActivityRepository.class), null, null,
                workTasks, acknowledgements(),
                new QueryServiceTemplate("test-app", new TestTransactionManager()));

        var first = service.query(caller, day, null, 1);
        assertTrue(first.isSuccess());
        assertEquals(List.of("WORK_TASK:31"), keys(first.getData().items()));
        assertEquals("WORK_TASK", first.getData().items().getFirst().subjectType());
        assertEquals(31L, first.getData().items().getFirst().subjectId());
        assertEquals(4L, first.getData().items().getFirst().sourceVersion());
        assertEquals("v3", decoded(first.getData().nextCursor())[0]);

        var second = service.query(caller, day, first.getData().nextCursor(), 1);
        assertEquals(List.of("WORK_TASK:32"), keys(second.getData().items()));
        assertNull(second.getData().nextCursor());
        verify(recordGuard, never()).visibleSubjectIds(any(), any(), any(), any());
    }

    @Test
    void upgradesLegacyV2CursorAndKeepsNewWorkTaskSourceExhausted() {
        OrganizationAccessGuard organizationGuard = mock(OrganizationAccessGuard.class);
        BusinessRecordAccessGuard recordGuard = mock(BusinessRecordAccessGuard.class);
        ActivityRepository activities = mock(ActivityRepository.class);
        WorkTaskRepository workTasks = mock(WorkTaskRepository.class);
        AuthenticatedCaller caller = new AuthenticatedCaller(7L, 9L,
                Set.of("worklist:read", "activities:read", "work-tasks:read"));
        LocalDate day = LocalDate.of(2026, 7, 18);
        List<Activity> values = List.of(activity(41L, day.atTime(9, 0)),
                activity(42L, day.atTime(10, 0)));
        when(organizationGuard.requireActiveMembership(caller, "worklist:read")).thenReturn(TENANT);
        when(activities.findDue(eq(TENANT), any(), any(), any(), any(), any(Integer.class)))
                .thenAnswer(invocation -> after(values, invocation.getArgument(3),
                        invocation.getArgument(4), invocation.getArgument(5)));
        when(recordGuard.visibleSubjectIds(eq(caller), eq(TENANT),
                eq(BusinessObjectType.CUSTOMER), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.Collection<Long> ids = invocation.getArgument(3);
            return Set.copyOf(ids);
        });
        WorklistQueryServiceImpl service = new WorklistQueryServiceImpl(organizationGuard,
                recordGuard, null, null, null, activities, null, null, workTasks,
                acknowledgements(),
                new QueryServiceTemplate("test-app", new TestTransactionManager()));

        String legacy = encoded("v2|" + day + "|S|E|E|E|E|E|E|E");
        var first = service.query(caller, day, legacy, 1);

        assertTrue(first.isSuccess());
        assertEquals(List.of("FOLLOW_UP:41"), keys(first.getData().items()));
        String[] upgraded = decoded(first.getData().nextCursor());
        assertEquals("v3", upgraded[0]);
        assertEquals(11, upgraded.length);
        assertEquals("E", upgraded[10]);
        verify(workTasks, never()).findDueForAssignee(any(), any(), any(), any(), any(), any(),
                any(Integer.class));
    }

    private WorklistQueryServiceImpl service(OrganizationAccessGuard organizationGuard,
                                             BusinessRecordAccessGuard recordGuard,
                                             ActivityRepository activities) {
        return new WorklistQueryServiceImpl(organizationGuard, recordGuard, null, null, null,
                activities, null, null, null, acknowledgements(),
                new QueryServiceTemplate("test-app", new TestTransactionManager()));
    }

    private WorklistAcknowledgementRepository acknowledgements() {
        WorklistAcknowledgementRepository repository = mock(WorklistAcknowledgementRepository.class);
        when(repository.findAcknowledged(any(), any(), any())).thenReturn(Set.of());
        return repository;
    }

    private List<Activity> activities(long firstId, int count) {
        LocalDateTime due = LocalDateTime.of(2026, 7, 18, 9, 0);
        return LongStream.range(firstId, firstId + count).mapToObj(id -> Activity.reconstitute(
                new Activity.Snapshot(new ActivityId(id), TENANT, BusinessObjectType.CUSTOMER,
                        id, ActivityKind.FOLLOW_UP, new MemberId(7L), "Call", "Follow up",
                        "content", due, due, List.of(), ActivityStatus.ACTIVE, 1L, due, due)))
                .toList();
    }

    private Activity activity(long id, LocalDateTime due) {
        return Activity.reconstitute(new Activity.Snapshot(new ActivityId(id), TENANT,
                BusinessObjectType.CUSTOMER, id, ActivityKind.FOLLOW_UP, new MemberId(7L),
                "Call", "Follow up", "content", due, due, List.of(), ActivityStatus.ACTIVE,
                1L, due, due));
    }

    private ReturnVisit returnVisit(long id, LocalDateTime due) {
        return ReturnVisit.reconstitute(new ReturnVisit.Snapshot(new ReturnVisitId(id), TENANT,
                new MemberId(7L), new CustomerId(id), null, null, "RV-" + id, due, null,
                null, null, ReturnVisitStatus.ACTIVE, List.of(), 1L, due, due));
    }

    private List<Activity> after(List<Activity> values, LocalDateTime afterTime, Long afterId, int limit) {
        return values.stream().filter(value -> afterTime == null
                        || value.getNextContactTime().isAfter(afterTime)
                        || value.getNextContactTime().equals(afterTime)
                        && value.getId().getValue() > afterId)
                .limit(limit).toList();
    }

    private List<ReturnVisit> afterVisits(List<ReturnVisit> values, LocalDateTime afterTime,
                                          Long afterId, int limit) {
        return values.stream().filter(value -> afterTime == null
                        || value.getVisitTime().isAfter(afterTime)
                        || value.getVisitTime().equals(afterTime)
                        && value.getId().getValue() > afterId)
                .limit(limit).toList();
    }

    private List<WorklistSourceRecord> afterRecords(
            List<WorklistSourceRecord> values, LocalDateTime afterTime, Long afterId, int limit) {
        return values.stream().filter(value -> afterTime == null || value.dueTime().isAfter(afterTime)
                        || value.dueTime().equals(afterTime) && value.sourceId() > afterId)
                .limit(limit).toList();
    }

    private List<WorkTaskWorklistRecord> afterWorkTasks(
            List<WorkTaskWorklistRecord> values, LocalDateTime afterTime, Long afterId, int limit) {
        return values.stream().filter(value -> afterTime == null || value.dueTime().isAfter(afterTime)
                        || value.dueTime().equals(afterTime)
                        && value.taskId().getValue() > afterId)
                .limit(limit).toList();
    }

    private String encoded(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }

    private String[] decoded(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
                .split("\\|", -1);
    }

    private List<String> keys(List<io.github.archetom.crm.application.vo.WorklistItemVO> values) {
        return values.stream().map(value -> value.kind() + ':' + value.sourceId()).toList();
    }
}
