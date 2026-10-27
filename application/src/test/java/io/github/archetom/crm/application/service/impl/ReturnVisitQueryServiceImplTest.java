package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitQueryRequest;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.security.ReturnVisitAccessResolver;
import io.github.archetom.crm.application.security.SubordinateOwnerResolver;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.ReturnVisitAccessMode;
import io.github.archetom.crm.domain.model.ReturnVisitQuery;
import io.github.archetom.crm.domain.model.ReturnVisitVisibility;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.entity.ReturnVisit;
import io.github.archetom.crm.domain.model.ReturnVisitStatus;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ReturnVisitFieldCatalog;
import io.github.archetom.crm.domain.repository.ReturnVisitRepository;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ReturnVisitId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReturnVisitQueryServiceImplTest {

    @Test
    void simpleOptionsUseVisibilityAndRequireUnmaskedDisplayFields() {
        AuthenticatedCaller caller = new AuthenticatedCaller(7L, 9L,
                Set.of(AuthorityCatalog.RETURN_VISITS_READ));
        TenantId tenantId = new TenantId(9L);
        MemberId memberId = new MemberId(7L);
        ReturnVisitRepository repository = mock(ReturnVisitRepository.class);
        ReturnVisitAccessResolver accessResolver = mock(ReturnVisitAccessResolver.class);
        BusinessRecordAccessGuard recordGuard = mock(BusinessRecordAccessGuard.class);
        SubordinateOwnerResolver subordinateOwnerResolver = mock(SubordinateOwnerResolver.class);
        ReturnVisitApplicationSupport support = mock(ReturnVisitApplicationSupport.class);
        SavedViewRepository savedViewRepository = mock(SavedViewRepository.class);
        ReturnVisitVisibility visibility = new ReturnVisitVisibility(DataScope.SELF, memberId,
                Set.of(), ReturnVisitAccessMode.READ);
        when(accessResolver.access(caller, AuthorityCatalog.RETURN_VISITS_READ,
                ReturnVisitAccessMode.READ)).thenReturn(new ReturnVisitAccessResolver.ReturnVisitAccess(
                tenantId, memberId, visibility));
        Map<FieldCode, EffectiveFieldAccess> fields = Map.of(
                new FieldCode(ReturnVisitFieldCatalog.NUMBER), EffectiveFieldAccess.owner(),
                new FieldCode(ReturnVisitFieldCatalog.VISIT_TIME), EffectiveFieldAccess.owner());
        when(support.accesses(tenantId, memberId)).thenReturn(fields);
        LocalDateTime due = LocalDateTime.of(2026, 7, 18, 9, 0);
        ReturnVisit visit = ReturnVisit.reconstitute(new ReturnVisit.Snapshot(
                new ReturnVisitId(3L), tenantId, memberId, new CustomerId(4L), null, null,
                "RV-3", due, null, null, null, ReturnVisitStatus.ACTIVE, List.of(), 1L, due, due));
        when(repository.findOptions(tenantId, visibility, "RV", 10)).thenReturn(List.of(visit));
        ReturnVisitQueryServiceImpl service = new ReturnVisitQueryServiceImpl(repository,
                accessResolver, recordGuard, subordinateOwnerResolver, savedViewRepository, support,
                new QueryServiceTemplate("test-app", new TestTransactionManager()));

        var result = service.options(caller, "RV", 10);

        assertTrue(result.isSuccess());
        assertEquals("RV-3", result.getData().getFirst().visitNumber());
        verify(support).requireReadableUnmasked(fields, ReturnVisitFieldCatalog.NUMBER);
        verify(support).requireReadableUnmasked(fields, ReturnVisitFieldCatalog.VISIT_TIME);
        verify(repository).findOptions(tenantId, visibility, "RV", 10);
    }

    @Test
    void rejectsInvisibleCustomerFilterBeforeQueryingReturnVisits() {
        AuthenticatedCaller caller = new AuthenticatedCaller(7L, 9L,
                Set.of(AuthorityCatalog.RETURN_VISITS_READ));
        TenantId tenantId = new TenantId(9L);
        ReturnVisitRepository repository = mock(ReturnVisitRepository.class);
        ReturnVisitAccessResolver accessResolver = mock(ReturnVisitAccessResolver.class);
        BusinessRecordAccessGuard recordGuard = mock(BusinessRecordAccessGuard.class);
        SubordinateOwnerResolver subordinateOwnerResolver = mock(SubordinateOwnerResolver.class);
        ReturnVisitApplicationSupport support = mock(ReturnVisitApplicationSupport.class);
        SavedViewRepository savedViewRepository = mock(SavedViewRepository.class);
        when(accessResolver.access(caller, AuthorityCatalog.RETURN_VISITS_READ,
                ReturnVisitAccessMode.READ)).thenReturn(new ReturnVisitAccessResolver.ReturnVisitAccess(
                tenantId, new MemberId(7L), new ReturnVisitVisibility(DataScope.TENANT,
                new MemberId(7L), Set.of(), ReturnVisitAccessMode.READ)));
        when(recordGuard.requireVisible(caller,
                io.github.archetom.crm.domain.model.BusinessObjectType.CUSTOMER, 42L, false))
                .thenThrow(new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED,
                        "customer is hidden"));
        ReturnVisitQueryServiceImpl service = new ReturnVisitQueryServiceImpl(repository,
                accessResolver, recordGuard, subordinateOwnerResolver, savedViewRepository, support,
                new QueryServiceTemplate("test-app", new TestTransactionManager()));

        var result = service.query(caller, new ReturnVisitQueryRequest().setCustomerId(42L));

        assertFalse(result.isSuccess());
        verify(repository, never()).findReturnVisits(eq(tenantId), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolvesTheSubordinateOwnerSceneOnTheServer() {
        AuthenticatedCaller caller = new AuthenticatedCaller(7L, 9L,
                Set.of(AuthorityCatalog.RETURN_VISITS_READ));
        TenantId tenantId = new TenantId(9L);
        MemberId memberId = new MemberId(7L);
        MemberId subordinate = new MemberId(8L);
        ReturnVisitRepository repository = mock(ReturnVisitRepository.class);
        ReturnVisitAccessResolver accessResolver = mock(ReturnVisitAccessResolver.class);
        BusinessRecordAccessGuard recordGuard = mock(BusinessRecordAccessGuard.class);
        SubordinateOwnerResolver subordinateOwnerResolver = mock(SubordinateOwnerResolver.class);
        ReturnVisitApplicationSupport support = mock(ReturnVisitApplicationSupport.class);
        SavedViewRepository savedViewRepository = mock(SavedViewRepository.class);
        ReturnVisitVisibility visibility = new ReturnVisitVisibility(DataScope.DEPARTMENT_SUBTREE,
                memberId, Set.of(new DepartmentId(1L)), ReturnVisitAccessMode.READ);
        when(accessResolver.access(caller, AuthorityCatalog.RETURN_VISITS_READ,
                ReturnVisitAccessMode.READ)).thenReturn(new ReturnVisitAccessResolver.ReturnVisitAccess(
                tenantId, memberId, visibility));
        when(support.accesses(tenantId, memberId)).thenReturn(Map.of(
                new FieldCode(ReturnVisitFieldCatalog.OWNER_ACTOR), EffectiveFieldAccess.owner()));
        when(support.fields(tenantId)).thenReturn(List.of());
        when(subordinateOwnerResolver.resolve(tenantId, memberId, DataScope.DEPARTMENT_SUBTREE))
                .thenReturn(Set.of(subordinate));
        when(repository.findReturnVisits(eq(tenantId), any())).thenReturn(
                new PageResult<>(1, 20, 0, List.of()));
        when(support.toVOs(eq(caller), eq(tenantId), any(), any())).thenReturn(List.of());
        ReturnVisitQueryServiceImpl service = new ReturnVisitQueryServiceImpl(repository,
                accessResolver, recordGuard, subordinateOwnerResolver, savedViewRepository, support,
                new QueryServiceTemplate("test-app", new TestTransactionManager()));

        ReturnVisitQueryRequest request = new ReturnVisitQueryRequest();
        request.setPage(1);
        request.setSize(20);
        request.setSubordinateOwnerOnly(true);
        var result = service.query(caller, request);

        assertTrue(result.isSuccess());
        org.mockito.ArgumentCaptor<ReturnVisitQuery> query = org.mockito.ArgumentCaptor
                .forClass(ReturnVisitQuery.class);
        verify(repository).findReturnVisits(eq(tenantId), query.capture());
        assertEquals(Set.of(subordinate), query.getValue().subordinateOwnerMemberIds());
        verify(subordinateOwnerResolver).resolve(tenantId, memberId, DataScope.DEPARTMENT_SUBTREE);
    }

    @Test
    void appliesNormalizedTemporaryAdvancedFilterBeforeQueryingReturnVisits() {
        AuthenticatedCaller caller = new AuthenticatedCaller(7L, 9L,
                Set.of(AuthorityCatalog.RETURN_VISITS_READ));
        TenantId tenantId = new TenantId(9L);
        MemberId memberId = new MemberId(7L);
        ReturnVisitRepository repository = mock(ReturnVisitRepository.class);
        ReturnVisitAccessResolver accessResolver = mock(ReturnVisitAccessResolver.class);
        BusinessRecordAccessGuard recordGuard = mock(BusinessRecordAccessGuard.class);
        SubordinateOwnerResolver subordinateOwnerResolver = mock(SubordinateOwnerResolver.class);
        SavedViewRepository savedViewRepository = mock(SavedViewRepository.class);
        ReturnVisitApplicationSupport support = mock(ReturnVisitApplicationSupport.class);
        ReturnVisitVisibility visibility = new ReturnVisitVisibility(DataScope.TENANT, memberId,
                Set.of(), ReturnVisitAccessMode.READ);
        when(accessResolver.access(caller, AuthorityCatalog.RETURN_VISITS_READ,
                ReturnVisitAccessMode.READ)).thenReturn(new ReturnVisitAccessResolver.ReturnVisitAccess(
                tenantId, memberId, visibility));
        when(support.accesses(tenantId, memberId)).thenReturn(Map.of(
                new FieldCode(ReturnVisitFieldCatalog.NUMBER), EffectiveFieldAccess.owner()));
        when(support.fields(tenantId)).thenReturn(ReturnVisitFieldCatalog.definitions(tenantId));
        when(repository.findReturnVisits(eq(tenantId), any())).thenReturn(
                new PageResult<>(1, 20, 0, List.of()));
        when(support.toVOs(eq(caller), eq(tenantId), any(), any())).thenReturn(List.of());
        ReturnVisitQueryServiceImpl service = new ReturnVisitQueryServiceImpl(repository,
                accessResolver, recordGuard, subordinateOwnerResolver, savedViewRepository, support,
                new QueryServiceTemplate("test-app", new TestTransactionManager()));
        ReturnVisitQueryRequest request = new ReturnVisitQueryRequest();
        request.setPage(1);
        request.setSize(20);
        request.setConditions(List.of(new FilterConditionRequest()
                .setFieldCode(ReturnVisitFieldCatalog.NUMBER)
                .setOperator("CONTAINS")
                .setValues(List.of("RV"))));

        var result = service.query(caller, request);

        assertTrue(result.isSuccess());
        org.mockito.ArgumentCaptor<ReturnVisitQuery> query = org.mockito.ArgumentCaptor
                .forClass(ReturnVisitQuery.class);
        verify(repository).findReturnVisits(eq(tenantId), query.capture());
        assertEquals(List.of(new io.github.archetom.crm.domain.entity.FilterCondition(
                new FieldCode(ReturnVisitFieldCatalog.NUMBER),
                io.github.archetom.crm.domain.model.FilterOperator.CONTAINS, List.of("RV"))),
                query.getValue().conditions());
    }

    @Test
    void rejectsCombiningSavedViewAndTemporaryAdvancedFilters() {
        AuthenticatedCaller caller = new AuthenticatedCaller(7L, 9L,
                Set.of(AuthorityCatalog.RETURN_VISITS_READ));
        TenantId tenantId = new TenantId(9L);
        MemberId memberId = new MemberId(7L);
        ReturnVisitRepository repository = mock(ReturnVisitRepository.class);
        ReturnVisitAccessResolver accessResolver = mock(ReturnVisitAccessResolver.class);
        BusinessRecordAccessGuard recordGuard = mock(BusinessRecordAccessGuard.class);
        SubordinateOwnerResolver subordinateOwnerResolver = mock(SubordinateOwnerResolver.class);
        SavedViewRepository savedViewRepository = mock(SavedViewRepository.class);
        ReturnVisitApplicationSupport support = mock(ReturnVisitApplicationSupport.class);
        when(accessResolver.access(caller, AuthorityCatalog.RETURN_VISITS_READ,
                ReturnVisitAccessMode.READ)).thenReturn(new ReturnVisitAccessResolver.ReturnVisitAccess(
                tenantId, memberId, new ReturnVisitVisibility(DataScope.TENANT, memberId,
                Set.of(), ReturnVisitAccessMode.READ)));
        ReturnVisitQueryServiceImpl service = new ReturnVisitQueryServiceImpl(repository,
                accessResolver, recordGuard, subordinateOwnerResolver, savedViewRepository, support,
                new QueryServiceTemplate("test-app", new TestTransactionManager()));
        ReturnVisitQueryRequest request = new ReturnVisitQueryRequest();
        request.setPage(1);
        request.setSize(20);
        request.setSavedViewId(3L);
        request.setConditions(List.of(new FilterConditionRequest()
                .setFieldCode(ReturnVisitFieldCatalog.NUMBER)
                .setOperator("CONTAINS")
                .setValues(List.of("RV"))));

        var result = service.query(caller, request);

        assertFalse(result.isSuccess());
        verify(repository, never()).findReturnVisits(eq(tenantId), any());
        verify(savedViewRepository, never()).findById(any(), any(), any());
    }
}
