package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.DepartmentCreateRequest;
import io.github.archetom.crm.api.dto.request.DepartmentMemberQueryRequest;
import io.github.archetom.crm.api.dto.request.DepartmentMoveRequest;
import io.github.archetom.crm.api.dto.request.MemberProfileQueryRequest;
import io.github.archetom.crm.api.dto.request.MembershipRequestCreateRequest;
import io.github.archetom.crm.api.dto.request.MembershipRequestQueryRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.application.security.CallerGuard;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.TenantAuthorizationChangeNotifier;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.entity.MemberProfile;
import io.github.archetom.crm.domain.entity.MembershipRequest;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.RoleAssignment;
import io.github.archetom.crm.domain.entity.TenantRole;
import io.github.archetom.crm.domain.entity.User;
import io.github.archetom.crm.domain.model.DepartmentStatus;
import io.github.archetom.crm.domain.model.MembershipRequestStatus;
import io.github.archetom.crm.domain.model.MembershipStatus;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.MemberProfileRepository;
import io.github.archetom.crm.domain.repository.MembershipRequestRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.RoleAssignmentRepository;
import io.github.archetom.crm.domain.repository.TenantRoleRepository;
import io.github.archetom.crm.domain.repository.UserRepository;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.MembershipId;
import io.github.archetom.crm.domain.valueobject.MembershipRequestId;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.UserId;
import io.github.archetom.crm.application.vo.TenantAccessVO;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceImplTest {

    private static final TenantId CURRENT_TENANT = new TenantId(99L);
    private static final MemberId CALLER_MEMBER = new MemberId(7L);

    @Mock
    private MemberProfileRepository memberProfileRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private OrganizationMembershipRepository membershipRepository;
    @Mock
    private OrganizationTenantRepository organizationTenantRepository;
    @Mock
    private MembershipRequestRepository membershipRequestRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;
    @Mock
    private TenantRoleRepository tenantRoleRepository;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private SystemRoleProvisioner systemRoleProvisioner;
    @Mock
    private CustomerFieldProvisioner customerFieldProvisioner;
    @Mock
    private LeadFieldProvisioner leadFieldProvisioner;
    @Mock
    private ContactFieldProvisioner contactFieldProvisioner;
    @Mock
    private OpportunityFieldProvisioner opportunityFieldProvisioner;
    @Mock
    private OpportunityPipelineProvisioner opportunityPipelineProvisioner;
    @Mock
    private ProductFieldProvisioner productFieldProvisioner;
    @Mock
    private ContractFieldProvisioner contractFieldProvisioner;
    @Mock
    private InvoiceFieldProvisioner invoiceFieldProvisioner;
    @Mock
    private ReceivableFieldProvisioner receivableFieldProvisioner;
    @Mock
    private ReceivablePlanFieldProvisioner receivablePlanFieldProvisioner;
    @Mock
    private ReturnVisitFieldProvisioner returnVisitFieldProvisioner;
    @Mock
    private OaRequestFieldProvisioner oaRequestFieldProvisioner;
    @Mock
    private TenantAuthorizationChangeNotifier authorizationChangeNotifier;

    private OrganizationServiceImpl service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());
        CallerGuard callerGuard = new CallerGuard();
        service = new OrganizationServiceImpl(
                memberProfileRepository,
                departmentRepository,
                membershipRepository,
                organizationTenantRepository,
                membershipRequestRepository,
                userRepository,
                roleAssignmentRepository,
                tenantRoleRepository,
                new CommandServiceTemplate("test-app", transactionManager),
                new QueryServiceTemplate("test-app", new TestTransactionManager()),
                callerGuard,
                new OrganizationAccessGuard(callerGuard, membershipRepository),
                systemRoleProvisioner,
                customerFieldProvisioner,
                leadFieldProvisioner,
                contactFieldProvisioner,
                opportunityFieldProvisioner,
                opportunityPipelineProvisioner,
                productFieldProvisioner,
                contractFieldProvisioner,
                invoiceFieldProvisioner,
                receivableFieldProvisioner,
                receivablePlanFieldProvisioner,
                returnVisitFieldProvisioner,
                oaRequestFieldProvisioner,
                authorizationChangeNotifier);
    }

    @Test
    void departmentQueriesFailClosedWithoutAnActiveLocalMembership() {
        when(membershipRepository.findByMember(CURRENT_TENANT, CALLER_MEMBER)).thenReturn(Optional.empty());

        Result<?> result = service.listDepartments(caller("organization:read"));

        assertFailure(result, "103");
        verifyNoInteractions(departmentRepository);
    }

    @Test
    void bootstrapUsesTheAtomicTenantMarkerBeforeWritingProfileOrMembership() {
        when(organizationTenantRepository.initialize(CURRENT_TENANT, CALLER_MEMBER)).thenReturn(false);

        Result<?> result = service.bootstrap(caller("organization:bootstrap"),
                new OrganizationBootstrapRequest().setDisplayName("First Member"));

        assertFailure(result, "302");
        verifyNoInteractions(memberProfileRepository);
        verify(membershipRepository, never()).save(any(), any());
    }

    @Test
    void bootstrapProvisionsCustomerLeadAndContactBuiltinFields() {
        when(organizationTenantRepository.initialize(CURRENT_TENANT, CALLER_MEMBER)).thenReturn(true);
        when(memberProfileRepository.findById(CALLER_MEMBER)).thenReturn(Optional.empty());
        when(membershipRepository.save(eq(CURRENT_TENANT), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        Result<?> result = service.bootstrap(caller("organization:bootstrap"),
                new OrganizationBootstrapRequest().setDisplayName("First Member"));

        assertTrue(result.isSuccess());
        verify(customerFieldProvisioner).provision(CURRENT_TENANT);
        verify(leadFieldProvisioner).provision(CURRENT_TENANT);
        verify(contactFieldProvisioner).provision(CURRENT_TENANT);
        verify(opportunityFieldProvisioner).provision(CURRENT_TENANT);
        verify(opportunityPipelineProvisioner).provision(CURRENT_TENANT);
        verify(productFieldProvisioner).provision(CURRENT_TENANT);
        verify(contractFieldProvisioner).provision(CURRENT_TENANT);
        verify(invoiceFieldProvisioner).provision(CURRENT_TENANT);
    }

    @Test
    void createDepartmentNeverLooksUpAParentOutsideTheCurrentTenant() {
        activeCallerMembership();
        DepartmentCreateRequest request = new DepartmentCreateRequest().setName("Sales").setParentDepartmentId(2L);
        when(departmentRepository.findById(CURRENT_TENANT, new DepartmentId(2L))).thenReturn(Optional.empty());

        Result<?> result = service.createDepartment(caller("organization:write"), request);

        assertFailure(result, "300");
        verify(departmentRepository).findById(CURRENT_TENANT, new DepartmentId(2L));
        verify(departmentRepository, never()).save(any(), any());
    }

    @Test
    void movingADepartmentBelowItsDescendantIsRejected() {
        activeCallerMembership();
        Department department = department(1L, null, DepartmentStatus.ACTIVE);
        Department descendant = department(2L, 1L, DepartmentStatus.ACTIVE);
        when(departmentRepository.findById(CURRENT_TENANT, new DepartmentId(1L))).thenReturn(Optional.of(department));
        when(departmentRepository.findById(CURRENT_TENANT, new DepartmentId(2L))).thenReturn(Optional.of(descendant));

        Result<?> result = service.moveDepartment(caller("organization:write"), 1L,
                new DepartmentMoveRequest().setParentDepartmentId(2L));

        assertFailure(result, "303");
        verify(departmentRepository, never()).save(any(), any());
    }

    @Test
    void approvingARequestCreatesTargetTenantMembership() {
        activeCallerMembership();
        MembershipRequest request = MembershipRequest.reconstitute(new MembershipRequest.Snapshot(
                new MembershipRequestId(12L), new MemberId(15L), CURRENT_TENANT, null, "Join",
                MembershipRequestStatus.PENDING, null, 0L, LocalDateTime.now(), LocalDateTime.now()));
        MemberProfile applicantProfile = MemberProfile.register(new MemberId(15L), "Applicant", null, null);
        when(membershipRequestRepository.findByIdForTarget(CURRENT_TENANT, new MembershipRequestId(12L)))
                .thenReturn(Optional.of(request));
        when(memberProfileRepository.findById(new MemberId(15L))).thenReturn(Optional.of(applicantProfile));
        when(membershipRepository.findByMember(CURRENT_TENANT, new MemberId(15L))).thenReturn(Optional.empty());
        when(membershipRepository.save(eq(CURRENT_TENANT), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(membershipRequestRepository.save(request)).thenReturn(request);

        Result<Void> result = service.approveMembershipRequest(caller("organization:write"), 12L);

        assertTrue(result.isSuccess());
        assertEquals(MembershipRequestStatus.APPROVED, request.getStatus());
        verify(membershipRepository).save(eq(CURRENT_TENANT), any(OrganizationMembership.class));
        verify(systemRoleProvisioner).provisionMember(CURRENT_TENANT, new MemberId(15L));
        verify(membershipRequestRepository).save(request);
    }

    @Test
    void treeWritesLockTheTenantBeforeReadingTheDepartmentPath() {
        activeCallerMembership();
        Department department = department(1L, null, DepartmentStatus.ACTIVE);
        Department descendant = department(2L, 1L, DepartmentStatus.ACTIVE);
        when(departmentRepository.findById(CURRENT_TENANT, new DepartmentId(1L))).thenReturn(Optional.of(department));
        when(departmentRepository.findById(CURRENT_TENANT, new DepartmentId(2L))).thenReturn(Optional.of(descendant));

        Result<?> result = service.moveDepartment(caller("organization:write"), 1L,
                new DepartmentMoveRequest().setParentDepartmentId(2L));

        assertFailure(result, "303");
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(organizationTenantRepository, departmentRepository);
        order.verify(organizationTenantRepository).lockForStructureChanges(CURRENT_TENANT);
        order.verify(departmentRepository).findById(CURRENT_TENANT, new DepartmentId(1L));
    }

    @Test
    void deletionRejectsInactiveChildrenWhileDeactivationUsesOnlyActiveDependencies() {
        activeCallerMembership();
        Department department = department(1L, null, DepartmentStatus.ACTIVE);
        when(departmentRepository.findById(CURRENT_TENANT, new DepartmentId(1L))).thenReturn(Optional.of(department));
        when(departmentRepository.existsActiveChild(CURRENT_TENANT, new DepartmentId(1L))).thenReturn(false);
        when(membershipRepository.existsActiveInDepartment(CURRENT_TENANT, new DepartmentId(1L))).thenReturn(false);
        when(departmentRepository.existsChild(CURRENT_TENANT, new DepartmentId(1L))).thenReturn(true);

        Result<Void> deactivation = service.deactivateDepartment(caller("organization:write"), 1L);
        Result<Void> deletion = service.deleteDepartment(caller("organization:write"), 1L);

        assertTrue(deactivation.isSuccess());
        assertFailure(deletion, "002");
        verify(membershipRepository, never()).existsInDepartment(CURRENT_TENANT, new DepartmentId(1L));
    }

    @Test
    void memberAndMembershipRequestQueriesUseBoundedRepositoryPages() {
        when(membershipRepository.findByMember(CURRENT_TENANT, CALLER_MEMBER))
                .thenReturn(Optional.of(OrganizationMembership.reconstitute(new OrganizationMembership.Snapshot(
                        new MembershipId(1L), CURRENT_TENANT, CALLER_MEMBER, null,
                        MembershipStatus.ACTIVE, 0L, LocalDateTime.now(), LocalDateTime.now()))));
        when(memberProfileRepository.findActiveByTenant(CURRENT_TENANT, "al", 2, 10))
                .thenReturn(new PageResult<>(2, 10, 11, java.util.List.of()));
        when(departmentRepository.findById(CURRENT_TENANT, new DepartmentId(4L)))
                .thenReturn(Optional.of(department(4L, null, DepartmentStatus.ACTIVE)));
        when(memberProfileRepository.findActiveByDepartment(CURRENT_TENANT, new DepartmentId(4L), "al", 2, 10))
                .thenReturn(new PageResult<>(2, 10, 7, java.util.List.of()));
        when(membershipRequestRepository.findByTargetTenant(CURRENT_TENANT, 3, 5))
                .thenReturn(new PageResult<>(3, 5, 12, java.util.List.of()));
        when(membershipRequestRepository.findByApplicant(CALLER_MEMBER, 3, 5))
                .thenReturn(new PageResult<>(3, 5, 4, java.util.List.of()));

        MemberProfileQueryRequest memberQuery = new MemberProfileQueryRequest().setKeyword("AL");
        memberQuery.setPage(2);
        memberQuery.setSize(10);
        MembershipRequestQueryRequest requestQuery = new MembershipRequestQueryRequest();
        requestQuery.setPage(3);
        requestQuery.setSize(5);
        Result<Pager<io.github.archetom.crm.application.vo.MemberProfileVO>> memberResult = service.queryMembers(
                caller("organization:read"), memberQuery);
        DepartmentMemberQueryRequest departmentMemberQuery = new DepartmentMemberQueryRequest();
        departmentMemberQuery.setKeyword("AL");
        departmentMemberQuery.setPage(2);
        departmentMemberQuery.setSize(10);
        Result<Pager<io.github.archetom.crm.application.vo.DepartmentMemberProfileVO>> departmentMemberResult =
                service.listDepartmentMembers(caller("organization:read"), 4L, departmentMemberQuery);
        Result<Pager<io.github.archetom.crm.application.vo.MembershipRequestVO>> requestResult =
                service.listMembershipRequests(caller("organization:write"), requestQuery);
        Result<Pager<io.github.archetom.crm.application.vo.MembershipRequestVO>> ownRequestResult =
                service.listMyMembershipRequests(caller("organization:request"), requestQuery);

        assertTrue(memberResult.isSuccess());
        assertEquals(11L, memberResult.getData().getTotalNum());
        assertTrue(departmentMemberResult.isSuccess());
        assertEquals(7L, departmentMemberResult.getData().getTotalNum());
        assertTrue(requestResult.isSuccess());
        assertEquals(12L, requestResult.getData().getTotalNum());
        assertTrue(ownRequestResult.isSuccess());
        assertEquals(4L, ownRequestResult.getData().getTotalNum());
        verify(memberProfileRepository).findActiveByTenant(CURRENT_TENANT, "al", 2, 10);
        verify(memberProfileRepository).findActiveByDepartment(CURRENT_TENANT, new DepartmentId(4L), "al", 2, 10);
        verify(membershipRequestRepository).findByTargetTenant(CURRENT_TENANT, 3, 5);
        verify(membershipRequestRepository).findByApplicant(CALLER_MEMBER, 3, 5);
    }

    @Test
    void deletedDepartmentIsNotVisibleToDepartmentMemberQueries() {
        when(membershipRepository.findByMember(CURRENT_TENANT, CALLER_MEMBER))
                .thenReturn(Optional.of(activeMembership(CURRENT_TENANT, CALLER_MEMBER)));
        when(departmentRepository.findById(CURRENT_TENANT, new DepartmentId(4L)))
                .thenReturn(Optional.of(department(4L, null, DepartmentStatus.DELETED)));

        Result<?> result = service.listDepartmentMembers(caller("organization:read"), 4L,
                new DepartmentMemberQueryRequest());

        assertFailure(result, "300");
        verify(memberProfileRepository, never()).findActiveByDepartment(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void departmentMemberQueryIncludesOnlyTheSelectedActiveSubtreeWhenRequested() {
        when(membershipRepository.findByMember(CURRENT_TENANT, CALLER_MEMBER))
                .thenReturn(Optional.of(activeMembership(CURRENT_TENANT, CALLER_MEMBER)));
        when(departmentRepository.findById(CURRENT_TENANT, new DepartmentId(4L)))
                .thenReturn(Optional.of(department(4L, null, DepartmentStatus.ACTIVE)));
        when(departmentRepository.findDescendants(CURRENT_TENANT, new DepartmentId(4L))).thenReturn(java.util.List.of(
                department(5L, 4L, DepartmentStatus.ACTIVE),
                department(6L, 4L, DepartmentStatus.INACTIVE),
                department(7L, 4L, DepartmentStatus.DELETED)));
        when(memberProfileRepository.findActiveByDepartments(
                CURRENT_TENANT, Set.of(new DepartmentId(4L), new DepartmentId(5L)), "al", 2, 10))
                .thenReturn(new PageResult<>(2, 10, 1, java.util.List.of()));

        DepartmentMemberQueryRequest request = new DepartmentMemberQueryRequest();
        request.setIncludeDescendants(true);
        request.setKeyword("AL");
        request.setPage(2);
        request.setSize(10);

        Result<?> result = service.listDepartmentMembers(caller("organization:read"), 4L, request);

        assertTrue(result.isSuccess());
        verify(memberProfileRepository).findActiveByDepartments(
                CURRENT_TENANT, Set.of(new DepartmentId(4L), new DepartmentId(5L)), "al", 2, 10);
        verify(memberProfileRepository, never()).findActiveByDepartment(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void departmentMemberRowsExposeAccountAndRolesOnlyWhenTheCallerHasTheMatchingAuthorities() {
        MemberId memberId = new MemberId(12L);
        DepartmentId departmentId = new DepartmentId(4L);
        MemberProfile profile = MemberProfile.register(memberId, "Sales User", null, null);
        OrganizationMembership membership = OrganizationMembership.reconstitute(new OrganizationMembership.Snapshot(
                new MembershipId(2L), CURRENT_TENANT, memberId, departmentId,
                MembershipStatus.ACTIVE, 0L, LocalDateTime.now(), LocalDateTime.now()));
        User user = mock(User.class);
        when(user.getId()).thenReturn(new UserId(12L));
        when(user.getUsernameValue()).thenReturn("sales.user");
        RoleId roleId = new RoleId(8L);
        TenantRole role = mock(TenantRole.class);
        when(role.getId()).thenReturn(roleId);
        when(role.getName()).thenReturn("Sales");
        when(role.isActive()).thenReturn(true);
        RoleAssignment assignment = RoleAssignment.reconstitute(new RoleAssignment.Snapshot(
                1L, CURRENT_TENANT, memberId, roleId, LocalDateTime.now()));
        when(membershipRepository.findByMember(CURRENT_TENANT, CALLER_MEMBER))
                .thenReturn(Optional.of(activeMembership(CURRENT_TENANT, CALLER_MEMBER)));
        when(departmentRepository.findById(CURRENT_TENANT, departmentId))
                .thenReturn(Optional.of(department(4L, null, DepartmentStatus.ACTIVE)));
        when(memberProfileRepository.findActiveByDepartment(CURRENT_TENANT, departmentId, null, 1, 20))
                .thenReturn(new PageResult<>(1, 20, 1, java.util.List.of(profile)));
        when(membershipRepository.findByMembers(CURRENT_TENANT, java.util.List.of(memberId)))
                .thenReturn(java.util.List.of(membership));
        when(departmentRepository.findByIds(CURRENT_TENANT, java.util.List.of(departmentId)))
                .thenReturn(java.util.List.of(department(4L, null, DepartmentStatus.ACTIVE)));
        when(userRepository.findByIds(CURRENT_TENANT, java.util.List.of(new UserId(12L))))
                .thenReturn(java.util.List.of(user));
        when(roleAssignmentRepository.findByMembers(CURRENT_TENANT, java.util.List.of(memberId)))
                .thenReturn(java.util.List.of(assignment));
        when(tenantRoleRepository.findByIds(CURRENT_TENANT, java.util.List.of(roleId)))
                .thenReturn(java.util.List.of(role));

        Result<Pager<io.github.archetom.crm.application.vo.DepartmentMemberProfileVO>> result =
                service.listDepartmentMembers(caller("organization:read", "users:read", "roles:read"), 4L,
                        new DepartmentMemberQueryRequest());

        assertTrue(result.isSuccess());
        assertEquals("Department 4", result.getData().getObjectList().getFirst().getDepartmentName());
        assertEquals("sales.user", result.getData().getObjectList().getFirst().getLoginName());
        assertEquals(java.util.List.of("Sales"), result.getData().getObjectList().getFirst().getRoleNames());
    }

    @Test
    void departmentMemberRowsDoNotResolveAccountOrRoleDetailsWithoutTheMatchingAuthorities() {
        MemberId memberId = new MemberId(12L);
        DepartmentId departmentId = new DepartmentId(4L);
        MemberProfile profile = MemberProfile.register(memberId, "Sales User", null, null);
        OrganizationMembership membership = OrganizationMembership.reconstitute(new OrganizationMembership.Snapshot(
                new MembershipId(2L), CURRENT_TENANT, memberId, departmentId,
                MembershipStatus.ACTIVE, 0L, LocalDateTime.now(), LocalDateTime.now()));
        when(membershipRepository.findByMember(CURRENT_TENANT, CALLER_MEMBER))
                .thenReturn(Optional.of(activeMembership(CURRENT_TENANT, CALLER_MEMBER)));
        when(departmentRepository.findById(CURRENT_TENANT, departmentId))
                .thenReturn(Optional.of(department(4L, null, DepartmentStatus.ACTIVE)));
        when(memberProfileRepository.findActiveByDepartment(CURRENT_TENANT, departmentId, null, 1, 20))
                .thenReturn(new PageResult<>(1, 20, 1, java.util.List.of(profile)));
        when(membershipRepository.findByMembers(CURRENT_TENANT, java.util.List.of(memberId)))
                .thenReturn(java.util.List.of(membership));
        when(departmentRepository.findByIds(CURRENT_TENANT, java.util.List.of(departmentId)))
                .thenReturn(java.util.List.of(department(4L, null, DepartmentStatus.ACTIVE)));

        Result<Pager<io.github.archetom.crm.application.vo.DepartmentMemberProfileVO>> result =
                service.listDepartmentMembers(caller("organization:read"), 4L, new DepartmentMemberQueryRequest());

        assertTrue(result.isSuccess());
        assertEquals("Department 4", result.getData().getObjectList().getFirst().getDepartmentName());
        assertNull(result.getData().getObjectList().getFirst().getLoginName());
        assertNull(result.getData().getObjectList().getFirst().getRoleNames());
        verifyNoInteractions(userRepository, roleAssignmentRepository, tenantRoleRepository);
    }

    @Test
    void membershipSubmissionLocksTheTargetBeforeCheckingEligibility() {
        TenantId targetTenant = new TenantId(88L);
        MemberProfile profile = MemberProfile.register(CALLER_MEMBER, "Applicant", null, null);
        when(organizationTenantRepository.lockForStructureChanges(targetTenant)).thenReturn(true);
        when(memberProfileRepository.findById(CALLER_MEMBER)).thenReturn(Optional.of(profile));
        when(membershipRepository.findByMember(targetTenant, CALLER_MEMBER)).thenReturn(Optional.empty());
        when(membershipRequestRepository.hasPending(CALLER_MEMBER, targetTenant)).thenReturn(false);
        when(membershipRequestRepository.save(any(MembershipRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Result<?> result = service.submitMembershipRequest(caller("organization:request"),
                new MembershipRequestCreateRequest().setTargetTenantId(88L));

        assertTrue(result.isSuccess());
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(
                organizationTenantRepository, memberProfileRepository, membershipRepository);
        order.verify(organizationTenantRepository).lockForStructureChanges(targetTenant);
        order.verify(memberProfileRepository).findById(CALLER_MEMBER);
        order.verify(membershipRepository).findByMember(targetTenant, CALLER_MEMBER);
    }

    @Test
    void targetAccessValidationReturnsDataOnlyAndDoesNotChangeCallerTenant() {
        AuthenticatedCaller caller = caller("organization:request");
        when(membershipRepository.findByMember(new TenantId(88L), CALLER_MEMBER)).thenReturn(Optional.empty());

        Result<TenantAccessVO> result = service.validateTenantAccess(caller, 88L);

        assertTrue(result.isSuccess());
        assertFalse(result.getData().isGranted());
        assertEquals(88L, result.getData().getTenantId());
        assertEquals(99L, caller.tenantId());
    }

    private void activeCallerMembership() {
        when(organizationTenantRepository.lockForStructureChanges(CURRENT_TENANT)).thenReturn(true);
        when(membershipRepository.findByMember(CURRENT_TENANT, CALLER_MEMBER))
                .thenReturn(Optional.of(activeMembership(CURRENT_TENANT, CALLER_MEMBER)));
    }

    private OrganizationMembership activeMembership(TenantId tenantId, MemberId memberId) {
        return OrganizationMembership.reconstitute(new OrganizationMembership.Snapshot(
                new MembershipId(1L), tenantId, memberId, null,
                MembershipStatus.ACTIVE, 0L, LocalDateTime.now(), LocalDateTime.now()));
    }

    private Department department(Long id, Long parentId, DepartmentStatus status) {
        return Department.reconstitute(new Department.Snapshot(
                new DepartmentId(id), CURRENT_TENANT,
                parentId == null ? null : new DepartmentId(parentId), "Department " + id,
                status, 0L, LocalDateTime.now(), LocalDateTime.now()));
    }

    private AuthenticatedCaller caller(String... authorities) {
        return new AuthenticatedCaller(7L, 99L, Set.of(authorities));
    }

    private void assertFailure(Result<?> result, String expectedSpecificCode) {
        assertFalse(result.isSuccess());
        assertEquals(expectedSpecificCode,
                result.getErrorContext().fetchRootError().getErrorCode().getErrorSpecific());
    }
}
