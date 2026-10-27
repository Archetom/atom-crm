package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.port.out.CustomerPoolCatalogQuery;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.application.service.attachment.AttachmentParentLifecycle;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.application.vo.CustomerPoolVO;
import io.github.archetom.crm.domain.entity.CustomerPool;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.TenantRole;
import io.github.archetom.crm.domain.model.SystemRoleKey;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.CustomerPoolRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.valueobject.CustomerPoolId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerPoolServiceImplTest {

    private static final TenantId TENANT = new TenantId(99L);
    private static final MemberId MEMBER = new MemberId(7L);
    private static final MemberId POOL_ADMINISTRATOR = new MemberId(8L);
    private static final DepartmentId DEPARTMENT = new DepartmentId(3L);

    @Mock private CustomerPoolRepository poolRepository;
    @Mock private CustomerPoolCatalogQuery poolCatalogQuery;
    @Mock private CustomerRepository customerRepository;
    @Mock private OrganizationMembershipRepository membershipRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private SavedViewRepository savedViewRepository;
    @Mock private FieldDefinitionRepository fieldRepository;
    @Mock private CustomerAccessResolver customerAccessResolver;
    @Mock private OrganizationAccessGuard accessGuard;
    @Mock private TenantAuthorizationEvaluator authorizationEvaluator;
    @Mock private CustomerApplicationSupport customerSupport;
    @Mock private CustomizationObjectLock objectLock;

    private CustomerPoolServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CustomerPoolServiceImpl(poolRepository, poolCatalogQuery, customerRepository,
                membershipRepository, departmentRepository, savedViewRepository, fieldRepository,
                customerAccessResolver, accessGuard, authorizationEvaluator, customerSupport,
                org.mockito.Mockito.mock(AttachmentParentLifecycle.class),
                org.mockito.Mockito.mock(io.github.archetom.crm.application.service.FileFieldBindingService.class),
                objectLock, org.mockito.Mockito.mock(io.github.archetom.crm.application.automation.AutomationScheduleManager.class),
                null, new QueryServiceTemplate(
                        "test-app", new TestTransactionManager()));
    }

    @Test
    void catalogIncludesTenantOwnerPoolsAndBatchesCustomerCounts() {
        OrganizationMembership membership = OrganizationMembership.activate(TENANT, MEMBER, DEPARTMENT);
        CustomerPool pool = pool(11L, "Owner-visible pool");
        when(accessGuard.requireActiveMembership(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(AuthorityCatalog.CUSTOMER_POOLS_READ)))
                .thenReturn(TENANT);
        when(membershipRepository.findByMember(TENANT, MEMBER)).thenReturn(Optional.of(membership));
        when(authorizationEvaluator.evaluate(TENANT, MEMBER)).thenReturn(ownerEvaluation(membership));
        when(poolCatalogQuery.findAccessible(TENANT, MEMBER, DEPARTMENT, true))
                .thenReturn(List.of(new CustomerPoolCatalogQuery.Entry(
                        pool.getId(), pool.getName(), pool.getStatus(), false)));
        when(poolRepository.countCustomers(TENANT, List.of(pool.getId())))
                .thenReturn(Map.of(pool.getId(), 4L));

        Result<List<CustomerPoolVO>> result = service.catalog(caller(AuthorityCatalog.CUSTOMER_POOLS_READ));

        assertTrue(result.isSuccess());
        assertEquals(1, result.getData().size());
        assertEquals(4L, result.getData().getFirst().customerCount());
        assertTrue(result.getData().getFirst().administrator());
        assertEquals(List.of(), result.getData().getFirst().administratorActorIds());
        assertEquals(List.of(), result.getData().getFirst().eligibleActorIds());
        assertEquals(List.of(), result.getData().getFirst().eligibleDepartmentIds());
        assertEquals(List.of(), result.getData().getFirst().rules());
        verify(authorizationEvaluator).evaluate(TENANT, MEMBER);
        verify(poolCatalogQuery).findAccessible(TENANT, MEMBER, DEPARTMENT, true);
        verify(poolRepository).countCustomers(TENANT, List.of(pool.getId()));
        verify(poolRepository, never()).countCustomers(TENANT, pool.getId());
    }

    @Test
    void managementPageEvaluatesOwnerOnceAndBatchesEveryPoolCount() {
        OrganizationMembership membership = OrganizationMembership.activate(TENANT, MEMBER, DEPARTMENT);
        CustomerPool first = pool(11L, "First pool");
        CustomerPool second = pool(22L, "Second pool");
        when(accessGuard.requireActiveMembership(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(AuthorityCatalog.CUSTOMER_POOLS_MANAGE)))
                .thenReturn(TENANT);
        when(authorizationEvaluator.evaluate(TENANT, MEMBER))
                .thenReturn(new TenantAuthorizationEvaluator.Evaluation(
                        membership, List.of(), List.of()));
        when(poolRepository.findAll(TENANT, 1, 20))
                .thenReturn(new PageResult<>(1, 20, 2, List.of(first, second)));
        when(poolRepository.countCustomers(TENANT, List.of(first.getId(), second.getId())))
                .thenReturn(Map.of(first.getId(), 2L, second.getId(), 3L));

        Result<Pager<CustomerPoolVO>> result = service.query(
                caller(AuthorityCatalog.CUSTOMER_POOLS_MANAGE), 1, 20);

        assertTrue(result.isSuccess());
        assertEquals(List.of(2L, 3L), result.getData().getObjectList().stream()
                .map(CustomerPoolVO::customerCount).toList());
        verify(authorizationEvaluator).evaluate(TENANT, MEMBER);
        verify(poolRepository).countCustomers(TENANT, List.of(first.getId(), second.getId()));
        verify(poolRepository, never()).countCustomers(
                org.mockito.ArgumentMatchers.eq(TENANT),
                org.mockito.ArgumentMatchers.any(CustomerPoolId.class));
    }

    private CustomerPool pool(long id, String name) {
        CustomerPool pool = CustomerPool.create(
                TENANT, name, Set.of(POOL_ADMINISTRATOR), Set.of(POOL_ADMINISTRATOR), Set.of(),
                0, 0, false, List.of());
        LocalDateTime now = LocalDateTime.of(2026, 7, 16, 9, 0);
        pool.onPersisted(new CustomerPoolId(id), 0L, now, now);
        return pool;
    }

    private TenantAuthorizationEvaluator.Evaluation ownerEvaluation(
            OrganizationMembership membership) {
        return new TenantAuthorizationEvaluator.Evaluation(membership,
                List.of(TenantRole.createSystem(TENANT, SystemRoleKey.TENANT_OWNER)), List.of());
    }

    private AuthenticatedCaller caller(String authority) {
        return new AuthenticatedCaller(MEMBER.getValue(), TENANT.getValue(), Set.of(authority));
    }
}
