package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.WorkTaskSeriesUpdateRequest;
import io.github.archetom.crm.application.automation.AutomationScheduleManager;
import io.github.archetom.crm.application.port.out.automation.AutomationScheduleStore;
import io.github.archetom.crm.application.port.out.automation.WorkRecurrenceControlStore;
import io.github.archetom.crm.application.properties.SchedulingProperties;
import io.github.archetom.crm.application.properties.WorkRecurrenceProperties;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.WorkTaskSeries;
import io.github.archetom.crm.domain.model.WorkRecurrenceFrequency;
import io.github.archetom.crm.domain.model.WorkRecurrenceRule;
import io.github.archetom.crm.domain.model.WorkTaskPriority;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.WorkTaskSeriesRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskSeriesId;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkTaskSeriesCommandServiceImplTest {
    @Test
    void updatesADraftTemplateWhileRecurrenceExecutionIsNotReady() {
        TenantId tenantId = new TenantId(1L);
        MemberId actor = new MemberId(2L);
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
        WorkTaskSeriesRepository seriesRepository = mock(WorkTaskSeriesRepository.class);
        OrganizationMembershipRepository memberships = mock(OrganizationMembershipRepository.class);
        OrganizationTenantRepository tenants = mock(OrganizationTenantRepository.class);
        OrganizationAccessGuard access = mock(OrganizationAccessGuard.class);
        TenantAuthorizationEvaluator authorization = mock(TenantAuthorizationEvaluator.class);
        AutomationScheduleManager schedules = mock(AutomationScheduleManager.class);
        AutomationScheduleStore scheduleStore = mock(AutomationScheduleStore.class);
        WorkRecurrenceControlStore control = mock(WorkRecurrenceControlStore.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        WorkTaskSeriesCommandServiceImpl service = new WorkTaskSeriesCommandServiceImpl(seriesRepository, memberships,
                tenants, access, authorization, schedules, scheduleStore, new SchedulingProperties(),
                new WorkRecurrenceProperties(), control, new WorkTaskSeriesApplicationSupport(),
                new CommandServiceTemplate("test", transactions));
        WorkTaskSeries draft = WorkTaskSeries.create(tenantId, actor, new MemberId(3L), List.of(), "Draft", null,
                WorkTaskPriority.NORMAL, rule(LocalDate.of(2026, 7, 23)), now);
        draft.onPersisted(new WorkTaskSeriesId(4L), 0L, now, now);
        AuthenticatedCaller caller = new AuthenticatedCaller(2L, 1L, Set.of(AuthorityCatalog.WORK_TASKS_WRITE));
        when(access.requireActiveMembership(caller, AuthorityCatalog.WORK_TASKS_WRITE)).thenReturn(tenantId);
        when(tenants.lockForTenantChanges(tenantId)).thenReturn(true);
        when(seriesRepository.findVisibleById(eq(tenantId), eq(new WorkTaskSeriesId(4L)), eq(actor), eq(false)))
                .thenReturn(Optional.of(draft));
        when(seriesRepository.findVisibleByIdForUpdate(eq(tenantId), eq(new WorkTaskSeriesId(4L)), eq(actor), eq(false)))
                .thenReturn(Optional.of(draft));
        when(scheduleStore.currentUtc()).thenReturn(now);
        when(memberships.findByMember(tenantId, new MemberId(3L))).thenReturn(Optional.of(
                OrganizationMembership.activate(tenantId, new MemberId(3L), null)));
        when(seriesRepository.save(tenantId, draft)).thenReturn(draft);
        when(seriesRepository.hasActive(tenantId)).thenReturn(false);

        assertTrue(service.update(caller, 4L, request()).isSuccess());

        verify(control, never()).requireCurrent(any());
        verify(seriesRepository).save(tenantId, draft);
    }

    private static WorkTaskSeriesUpdateRequest request() {
        return new WorkTaskSeriesUpdateRequest().setTitle("Edited").setPriority("NORMAL")
                .setAssigneeActorId(3L).setFollowerActorIds(List.of()).setRecurrenceFrequency("DAILY")
                .setRecurrenceInterval(1).setWeekdays(List.of()).setLastDayOfMonth(false).setZoneId("UTC")
                .setStartLocalDate(LocalDate.of(2026, 7, 23)).setLocalTime(LocalTime.of(9, 0)).setVersion(0L);
    }

    private static WorkRecurrenceRule rule(LocalDate start) {
        return new WorkRecurrenceRule(WorkRecurrenceFrequency.DAILY, 1, Set.of(), null, false,
                "UTC", start, LocalTime.of(9, 0), null);
    }
}
