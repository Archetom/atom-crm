package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.DepartmentCreateRequest;
import io.github.archetom.crm.api.dto.request.DepartmentMemberQueryRequest;
import io.github.archetom.crm.api.dto.request.DepartmentMoveRequest;
import io.github.archetom.crm.api.dto.request.DepartmentRenameRequest;
import io.github.archetom.crm.api.dto.request.MemberProfileUpdateRequest;
import io.github.archetom.crm.api.dto.request.MemberProfileQueryRequest;
import io.github.archetom.crm.api.dto.request.MembershipRequestCreateRequest;
import io.github.archetom.crm.api.dto.request.MembershipRequestQueryRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.application.assembler.OrganizationAssembler;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.CallerGuard;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.TenantAuthorizationChangeNotifier;
import io.github.archetom.crm.application.service.OrganizationService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.DepartmentVO;
import io.github.archetom.crm.application.vo.DepartmentMemberProfileVO;
import io.github.archetom.crm.application.vo.MemberProfileVO;
import io.github.archetom.crm.application.vo.MembershipRequestVO;
import io.github.archetom.crm.application.vo.OrganizationMembershipVO;
import io.github.archetom.crm.application.vo.TenantAccessVO;
import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.entity.MemberProfile;
import io.github.archetom.crm.domain.entity.MembershipRequest;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.RoleAssignment;
import io.github.archetom.crm.domain.entity.TenantRole;
import io.github.archetom.crm.domain.entity.User;
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
import io.github.archetom.crm.domain.valueobject.Email;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.MembershipRequestId;
import io.github.archetom.crm.domain.valueobject.PhoneNumber;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.UserId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Result;
import io.github.archetom.common.result.Pager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Explicit organization use cases with membership checks at every tenant-owned boundary. */
@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private static final String ORGANIZATION_BOOTSTRAP = "organization:bootstrap";
    private static final String ORGANIZATION_READ = "organization:read";
    private static final String ORGANIZATION_WRITE = "organization:write";
    private static final String ORGANIZATION_REQUEST = "organization:request";
    private static final String ROLES_READ = "roles:read";
    private static final String USERS_READ = "users:read";

    private final MemberProfileRepository memberProfileRepository;
    private final DepartmentRepository departmentRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationTenantRepository organizationTenantRepository;
    private final MembershipRequestRepository membershipRequestRepository;
    private final UserRepository userRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final TenantRoleRepository tenantRoleRepository;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;
    private final CallerGuard callerGuard;
    private final OrganizationAccessGuard organizationAccessGuard;
    private final SystemRoleProvisioner systemRoleProvisioner;
    private final CustomerFieldProvisioner customerFieldProvisioner;
    private final LeadFieldProvisioner leadFieldProvisioner;
    private final ContactFieldProvisioner contactFieldProvisioner;
    private final OpportunityFieldProvisioner opportunityFieldProvisioner;
    private final OpportunityPipelineProvisioner opportunityPipelineProvisioner;
    private final ProductFieldProvisioner productFieldProvisioner;
    private final ContractFieldProvisioner contractFieldProvisioner;
    private final InvoiceFieldProvisioner invoiceFieldProvisioner;
    private final ReceivableFieldProvisioner receivableFieldProvisioner;
    private final ReceivablePlanFieldProvisioner receivablePlanFieldProvisioner;
    private final ReturnVisitFieldProvisioner returnVisitFieldProvisioner;
    private final OaRequestFieldProvisioner oaRequestFieldProvisioner;
    private final TenantAuthorizationChangeNotifier authorizationChangeNotifier;

    @Override
    public Result<OrganizationMembershipVO> bootstrap(AuthenticatedCaller caller, OrganizationBootstrapRequest request) {
        return commandTemplate.execute(UseCaseOperation.ORGANIZATION_BOOTSTRAP, new ServiceOperation<>() {
            private TenantId tenantId;
            private MemberId memberId;
            private OrganizationMembership membership;

            @Override
            public void validate() {
                tenantId = callerGuard.requireTenant(caller, ORGANIZATION_BOOTSTRAP);
                memberId = memberId(caller);
                requireRequest(request, "Organization bootstrap request");
            }

            @Override
            public OrganizationMembershipVO execute() {
                if (!organizationTenantRepository.initialize(tenantId, memberId)) {
                    throw applicationError(ApplicationErrorCode.RESOURCE_ALREADY_EXISTS,
                            "This tenant has already been initialized");
                }
                MemberProfile profile = memberProfileRepository.findById(memberId)
                        .map(existing -> updateProfile(existing, request.getDisplayName(), request.getEmail(), request.getPhoneNumber()))
                        .orElseGet(() -> MemberProfile.register(memberId, request.getDisplayName(),
                                optionalEmail(request.getEmail()), optionalPhone(request.getPhoneNumber())));
                memberProfileRepository.save(profile);
                membership = membershipRepository.save(tenantId,
                        OrganizationMembership.activate(tenantId, memberId, null));
                systemRoleProvisioner.provisionOwner(tenantId, memberId);
                customerFieldProvisioner.provision(tenantId);
                leadFieldProvisioner.provision(tenantId);
                contactFieldProvisioner.provision(tenantId);
                opportunityFieldProvisioner.provision(tenantId);
                opportunityPipelineProvisioner.provision(tenantId);
                productFieldProvisioner.provision(tenantId);
                contractFieldProvisioner.provision(tenantId);
                invoiceFieldProvisioner.provision(tenantId);
                receivableFieldProvisioner.provision(tenantId);
                receivablePlanFieldProvisioner.provision(tenantId);
                returnVisitFieldProvisioner.provision(tenantId);
                oaRequestFieldProvisioner.provision(tenantId);
                authorizationChangeNotifier.changed(tenantId);
                return OrganizationAssembler.toVO(membership);
            }
        });
    }

    @Override
    public Result<MemberProfileVO> updateMyProfile(AuthenticatedCaller caller, MemberProfileUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.MEMBER_PROFILE_UPDATE, new ServiceOperation<>() {
            private MemberId memberId;

            @Override
            public void validate() {
                callerGuard.requireTenant(caller, ORGANIZATION_REQUEST);
                memberId = memberId(caller);
                requireRequest(request, "Member profile request");
            }

            @Override
            public MemberProfileVO execute() {
                MemberProfile profile = memberProfileRepository.findById(memberId)
                        .map(existing -> updateProfile(existing, request.getDisplayName(), request.getEmail(), request.getPhoneNumber()))
                        .orElseGet(() -> MemberProfile.register(memberId, request.getDisplayName(),
                                optionalEmail(request.getEmail()), optionalPhone(request.getPhoneNumber())));
                return OrganizationAssembler.toVO(memberProfileRepository.save(profile));
            }
        });
    }

    @Override
    public Result<DepartmentVO> createDepartment(AuthenticatedCaller caller, DepartmentCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.DEPARTMENT_CREATE, new ServiceOperation<>() {
            private TenantId tenantId;
            private DepartmentId parentId;

            @Override
            public void validate() {
                tenantId = organizationAccessGuard.requireActiveMembership(caller, ORGANIZATION_WRITE);
                requireRequest(request, "Department create request");
                parentId = optionalDepartmentId(request.getParentDepartmentId());
            }

            @Override
            public DepartmentVO execute() {
                lockDepartmentStructure(tenantId);
                requireActiveParent(tenantId, parentId);
                return OrganizationAssembler.toVO(departmentRepository.save(tenantId,
                        Department.create(tenantId, parentId, request.getName())));
            }
        });
    }

    @Override
    public Result<DepartmentVO> renameDepartment(
            AuthenticatedCaller caller, Long departmentId, DepartmentRenameRequest request) {
        return commandTemplate.execute(UseCaseOperation.DEPARTMENT_RENAME, new ServiceOperation<>() {
            private TenantId tenantId;
            private DepartmentId id;

            @Override
            public void validate() {
                tenantId = organizationAccessGuard.requireActiveMembership(caller, ORGANIZATION_WRITE);
                id = requiredDepartmentId(departmentId);
                requireRequest(request, "Department rename request");
            }

            @Override
            public DepartmentVO execute() {
                Department department = findDepartment(tenantId, id);
                department.rename(request.getName());
                return OrganizationAssembler.toVO(departmentRepository.save(tenantId, department));
            }
        });
    }

    @Override
    public Result<DepartmentVO> moveDepartment(
            AuthenticatedCaller caller, Long departmentId, DepartmentMoveRequest request) {
        return commandTemplate.execute(UseCaseOperation.DEPARTMENT_MOVE, new ServiceOperation<>() {
            private TenantId tenantId;
            private DepartmentId id;
            private DepartmentId parentId;

            @Override
            public void validate() {
                tenantId = organizationAccessGuard.requireActiveMembership(caller, ORGANIZATION_WRITE);
                id = requiredDepartmentId(departmentId);
                requireRequest(request, "Department move request");
                parentId = optionalDepartmentId(request.getParentDepartmentId());
            }

            @Override
            public DepartmentVO execute() {
                lockDepartmentStructure(tenantId);
                Department department = findDepartment(tenantId, id);
                requireValidMoveParent(tenantId, department, parentId);
                department.moveTo(parentId);
                return OrganizationAssembler.toVO(departmentRepository.save(tenantId, department));
            }
        });
    }

    @Override
    public Result<Void> deactivateDepartment(AuthenticatedCaller caller, Long departmentId) {
        return commandTemplate.execute(UseCaseOperation.DEPARTMENT_DEACTIVATE, new ServiceOperation<>() {
            private TenantId tenantId;
            private DepartmentId id;

            @Override
            public void validate() {
                tenantId = organizationAccessGuard.requireActiveMembership(caller, ORGANIZATION_WRITE);
                id = requiredDepartmentId(departmentId);
            }

            @Override
            public Void execute() {
                lockDepartmentStructure(tenantId);
                Department department = findDepartment(tenantId, id);
                rejectIfDepartmentCannotDeactivate(tenantId, id);
                department.deactivate();
                departmentRepository.save(tenantId, department);
                return null;
            }
        });
    }

    @Override
    public Result<Void> deleteDepartment(AuthenticatedCaller caller, Long departmentId) {
        return commandTemplate.execute(UseCaseOperation.DEPARTMENT_DELETE, new ServiceOperation<>() {
            private TenantId tenantId;
            private DepartmentId id;

            @Override
            public void validate() {
                tenantId = organizationAccessGuard.requireActiveMembership(caller, ORGANIZATION_WRITE);
                id = requiredDepartmentId(departmentId);
            }

            @Override
            public Void execute() {
                lockDepartmentStructure(tenantId);
                Department department = findDepartment(tenantId, id);
                rejectIfDepartmentCannotDelete(tenantId, id);
                department.delete();
                departmentRepository.save(tenantId, department);
                return null;
            }
        });
    }

    @Override
    public Result<List<DepartmentVO>> listDepartments(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.DEPARTMENT_LIST, new ServiceOperation<>() {
            private TenantId tenantId;

            @Override
            public void validate() {
                tenantId = organizationAccessGuard.requireActiveMembership(caller, ORGANIZATION_READ);
            }

            @Override
            public List<DepartmentVO> execute() {
                return departmentRepository.findAll(tenantId).stream()
                        .filter(department -> department.getStatus() != io.github.archetom.crm.domain.model.DepartmentStatus.DELETED)
                        .map(OrganizationAssembler::toVO)
                        .toList();
            }
        });
    }

    @Override
    public Result<List<DepartmentVO>> findDepartmentsByIds(AuthenticatedCaller caller, List<Long> departmentIds) {
        return queryTemplate.execute(UseCaseOperation.DEPARTMENT_BATCH_GET, new ServiceOperation<>() {
            private TenantId tenantId;
            private List<DepartmentId> ids;

            @Override
            public void validate() {
                tenantId = organizationAccessGuard.requireActiveMembership(caller, ORGANIZATION_READ);
                ids = requiredDepartmentIds(departmentIds);
            }

            @Override
            public List<DepartmentVO> execute() {
                java.util.Map<Long, Department> departmentsById = departmentRepository.findByIds(tenantId, ids).stream()
                        .filter(department -> department.getStatus() != io.github.archetom.crm.domain.model.DepartmentStatus.DELETED)
                        .collect(java.util.stream.Collectors.toMap(
                                department -> department.getId().getValue(), department -> department));
                return ids.stream().map(DepartmentId::getValue).map(departmentsById::get)
                        .filter(java.util.Objects::nonNull).map(OrganizationAssembler::toVO).toList();
            }
        });
    }

    @Override
    public Result<List<DepartmentVO>> listDepartmentDescendants(AuthenticatedCaller caller, Long departmentId) {
        return queryTemplate.execute(UseCaseOperation.DEPARTMENT_DESCENDANT_LIST, new ServiceOperation<>() {
            private TenantId tenantId;
            private DepartmentId id;

            @Override
            public void validate() {
                tenantId = organizationAccessGuard.requireActiveMembership(caller, ORGANIZATION_READ);
                id = requiredDepartmentId(departmentId);
            }

            @Override
            public List<DepartmentVO> execute() {
                findVisibleDepartment(tenantId, id);
                return departmentRepository.findDescendants(tenantId, id).stream()
                        .filter(descendant -> descendant.getStatus() != io.github.archetom.crm.domain.model.DepartmentStatus.DELETED)
                        .map(OrganizationAssembler::toVO)
                        .toList();
            }
        });
    }

    @Override
    public Result<Pager<DepartmentMemberProfileVO>> listDepartmentMembers(
            AuthenticatedCaller caller, Long departmentId, DepartmentMemberQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.DEPARTMENT_MEMBER_LIST, new ServiceOperation<>() {
            private TenantId tenantId;
            private DepartmentId id;
            private String keyword;

            @Override
            public void validate() {
                tenantId = organizationAccessGuard.requireActiveMembership(caller, ORGANIZATION_READ);
                id = requiredDepartmentId(departmentId);
                validatePageRequest(request, "Department member query");
                keyword = normalizeKeyword(request.getKeyword());
            }

            @Override
            public Pager<DepartmentMemberProfileVO> execute() {
                findVisibleDepartment(tenantId, id);
                PageResult<MemberProfile> profiles;
                if (request.isIncludeDescendants()) {
                    LinkedHashSet<DepartmentId> departmentIds = new LinkedHashSet<>();
                    departmentIds.add(id);
                    departmentRepository.findDescendants(tenantId, id).stream()
                            .filter(Department::isActive)
                            .map(Department::getId)
                            .forEach(departmentIds::add);
                    profiles = memberProfileRepository.findActiveByDepartments(
                            tenantId, departmentIds, keyword, request.getPage(), request.getSize());
                } else {
                    profiles = memberProfileRepository.findActiveByDepartment(
                            tenantId, id, keyword, request.getPage(), request.getSize());
                }
                return toDepartmentMemberProfilePager(caller, tenantId, profiles);
            }
        });
    }

    private Pager<DepartmentMemberProfileVO> toDepartmentMemberProfilePager(
            AuthenticatedCaller caller, TenantId tenantId, PageResult<MemberProfile> profiles) {
        List<MemberId> memberIds = profiles.items().stream().map(MemberProfile::getId).toList();
        if (memberIds.isEmpty()) {
            return OrganizationAssembler.toDepartmentMemberProfilePager(profiles, Map.of(),
                    caller.hasAuthority(USERS_READ) ? Map.of() : null,
                    caller.hasAuthority(ROLES_READ) ? Map.of() : null);
        }

        Map<Long, OrganizationMembership> membershipsByMember = membershipRepository
                .findByMembers(tenantId, memberIds).stream()
                .filter(OrganizationMembership::isActive)
                .collect(Collectors.toMap(membership -> membership.getMemberId().getValue(), membership -> membership,
                        (first, ignored) -> first, LinkedHashMap::new));
        List<DepartmentId> departmentIds = membershipsByMember.values().stream()
                .map(OrganizationMembership::getDepartmentId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> departmentNamesById = departmentRepository.findByIds(tenantId, departmentIds).stream()
                .filter(department -> department.getStatus()
                        != io.github.archetom.crm.domain.model.DepartmentStatus.DELETED)
                .collect(Collectors.toMap(department -> department.getId().getValue(), Department::getName));
        Map<Long, String> departmentNamesByMember = new LinkedHashMap<>();
        for (Map.Entry<Long, OrganizationMembership> entry : membershipsByMember.entrySet()) {
            DepartmentId memberDepartmentId = entry.getValue().getDepartmentId();
            if (memberDepartmentId != null) {
                String departmentName = departmentNamesById.get(memberDepartmentId.getValue());
                if (departmentName != null) {
                    departmentNamesByMember.put(entry.getKey(), departmentName);
                }
            }
        }

        Map<Long, String> loginNamesByMember = caller.hasAuthority(USERS_READ)
                ? userRepository.findByIds(tenantId, memberIds.stream().map(memberId -> new UserId(memberId.getValue()))
                        .toList()).stream().collect(Collectors.toMap(user -> user.getId().getValue(),
                        User::getUsernameValue))
                : null;
        Map<Long, List<String>> roleNamesByMember = caller.hasAuthority(ROLES_READ)
                ? roleNamesByMember(tenantId, memberIds) : null;
        return OrganizationAssembler.toDepartmentMemberProfilePager(profiles, departmentNamesByMember,
                loginNamesByMember, roleNamesByMember);
    }

    private Map<Long, List<String>> roleNamesByMember(TenantId tenantId, List<MemberId> memberIds) {
        List<RoleAssignment> assignments = roleAssignmentRepository.findByMembers(tenantId, memberIds);
        List<RoleId> roleIds = assignments.stream().map(RoleAssignment::getRoleId).distinct().toList();
        Map<RoleId, String> activeRoleNames = tenantRoleRepository.findByIds(tenantId, roleIds).stream()
                .filter(TenantRole::isActive)
                .collect(Collectors.toMap(TenantRole::getId, TenantRole::getName));
        Map<Long, List<String>> roleNames = new LinkedHashMap<>();
        for (RoleAssignment assignment : assignments) {
            String roleName = activeRoleNames.get(assignment.getRoleId());
            if (roleName != null) {
                roleNames.computeIfAbsent(assignment.getMemberId().getValue(), ignored -> new java.util.ArrayList<>())
                        .add(roleName);
            }
        }
        roleNames.replaceAll((memberId, names) -> names.stream().distinct().sorted().toList());
        return roleNames;
    }

    @Override
    public Result<Pager<MemberProfileVO>> queryMembers(AuthenticatedCaller caller, MemberProfileQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.MEMBER_PROFILE_QUERY, new ServiceOperation<>() {
            private TenantId tenantId;
            private String keyword;

            @Override
            public void validate() {
                tenantId = organizationAccessGuard.requireActiveMembership(caller, ORGANIZATION_READ);
                validatePageRequest(request, "Member query request");
                keyword = normalizeKeyword(request.getKeyword());
            }

            @Override
            public Pager<MemberProfileVO> execute() {
                PageResult<MemberProfile> profiles = memberProfileRepository.findActiveByTenant(
                        tenantId, keyword, request.getPage(), request.getSize());
                return OrganizationAssembler.toMemberProfilePager(profiles);
            }
        });
    }

    @Override
    public Result<MembershipRequestVO> submitMembershipRequest(
            AuthenticatedCaller caller, MembershipRequestCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.MEMBERSHIP_REQUEST_SUBMIT, new ServiceOperation<>() {
            private MemberId applicantId;
            private TenantId targetTenantId;
            private DepartmentId targetDepartmentId;

            @Override
            public void validate() {
                callerGuard.requireTenant(caller, ORGANIZATION_REQUEST);
                applicantId = memberId(caller);
                requireRequest(request, "Membership request");
                targetTenantId = requiredTenantId(request.getTargetTenantId());
                targetDepartmentId = optionalDepartmentId(request.getTargetDepartmentId());
            }

            @Override
            public MembershipRequestVO execute() {
                lockDepartmentStructure(targetTenantId);
                if (memberProfileRepository.findById(applicantId).filter(MemberProfile::isActive).isEmpty()) {
                    throw applicationError(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                            "Create an active member profile before requesting membership");
                }
                if (membershipRepository.findByMember(targetTenantId, applicantId)
                        .filter(OrganizationMembership::isActive).isPresent()) {
                    throw applicationError(ApplicationErrorCode.RESOURCE_ALREADY_EXISTS,
                            "Caller already has access to the target tenant");
                }
                if (membershipRequestRepository.hasPending(applicantId, targetTenantId)) {
                    throw applicationError(ApplicationErrorCode.RESOURCE_ALREADY_EXISTS,
                            "Caller already has a pending request for the target tenant");
                }
                requireActiveParent(targetTenantId, targetDepartmentId);
                MembershipRequest membershipRequest = MembershipRequest.submit(applicantId, targetTenantId,
                        targetDepartmentId, request.getMessage());
                return OrganizationAssembler.toVO(membershipRequestRepository.save(membershipRequest));
            }
        });
    }

    @Override
    public Result<Void> withdrawMembershipRequest(AuthenticatedCaller caller, Long requestId) {
        return commandTemplate.execute(UseCaseOperation.MEMBERSHIP_REQUEST_WITHDRAW, new ServiceOperation<>() {
            private MemberId applicantId;
            private MembershipRequestId id;

            @Override
            public void validate() {
                callerGuard.requireTenant(caller, ORGANIZATION_REQUEST);
                applicantId = memberId(caller);
                id = requiredMembershipRequestId(requestId);
            }

            @Override
            public Void execute() {
                MembershipRequest membershipRequest = membershipRequestRepository
                        .findByIdForApplicant(applicantId, id)
                        .orElseThrow(() -> applicationError(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                                "Membership request does not exist"));
                membershipRequest.withdraw(applicantId);
                membershipRequestRepository.save(membershipRequest);
                return null;
            }
        });
    }

    @Override
    public Result<Pager<MembershipRequestVO>> listMembershipRequests(
            AuthenticatedCaller caller, MembershipRequestQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.MEMBERSHIP_REQUEST_LIST, new ServiceOperation<>() {
            private TenantId tenantId;

            @Override
            public void validate() {
                tenantId = organizationAccessGuard.requireActiveMembership(caller, ORGANIZATION_WRITE);
                validatePageRequest(request, "Membership request query");
            }

            @Override
            public Pager<MembershipRequestVO> execute() {
                return OrganizationAssembler.toMembershipRequestPager(
                        membershipRequestRepository.findByTargetTenant(tenantId, request.getPage(), request.getSize()));
            }
        });
    }

    @Override
    public Result<Pager<MembershipRequestVO>> listMyMembershipRequests(
            AuthenticatedCaller caller, MembershipRequestQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.MEMBERSHIP_REQUEST_MINE_LIST, new ServiceOperation<>() {
            private MemberId applicantId;

            @Override
            public void validate() {
                callerGuard.requireTenant(caller, ORGANIZATION_REQUEST);
                applicantId = memberId(caller);
                validatePageRequest(request, "My membership request query");
            }

            @Override
            public Pager<MembershipRequestVO> execute() {
                return OrganizationAssembler.toMembershipRequestPager(
                        membershipRequestRepository.findByApplicant(applicantId, request.getPage(), request.getSize()));
            }
        });
    }

    @Override
    public Result<Void> approveMembershipRequest(AuthenticatedCaller caller, Long requestId) {
        return decideMembershipRequest(caller, requestId, true);
    }

    @Override
    public Result<Void> rejectMembershipRequest(AuthenticatedCaller caller, Long requestId) {
        return decideMembershipRequest(caller, requestId, false);
    }

    @Override
    public Result<List<TenantAccessVO>> listAccessibleTenants(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.TENANT_ACCESS_LIST, new ServiceOperation<>() {
            private MemberId memberId;

            @Override
            public void validate() {
                callerGuard.requireTenant(caller, ORGANIZATION_REQUEST);
                memberId = memberId(caller);
            }

            @Override
            public List<TenantAccessVO> execute() {
                return membershipRepository.findActiveByMember(memberId).stream()
                        .map(OrganizationAssembler::toTenantAccessVO)
                        .toList();
            }
        });
    }

    @Override
    public Result<TenantAccessVO> validateTenantAccess(AuthenticatedCaller caller, Long targetTenantId) {
        return queryTemplate.execute(UseCaseOperation.TENANT_ACCESS_VALIDATE, new ServiceOperation<>() {
            private MemberId memberId;
            private TenantId targetId;

            @Override
            public void validate() {
                callerGuard.requireTenant(caller, ORGANIZATION_REQUEST);
                memberId = memberId(caller);
                targetId = requiredTenantId(targetTenantId);
            }

            @Override
            public TenantAccessVO execute() {
                return membershipRepository.findByMember(targetId, memberId)
                        .filter(OrganizationMembership::isActive)
                        .map(OrganizationAssembler::toTenantAccessVO)
                        .orElseGet(() -> new TenantAccessVO(targetId.getValue(), null, false));
            }
        });
    }

    private Result<Void> decideMembershipRequest(AuthenticatedCaller caller, Long requestId, boolean approve) {
        UseCaseOperation operation = approve
                ? UseCaseOperation.MEMBERSHIP_REQUEST_APPROVE : UseCaseOperation.MEMBERSHIP_REQUEST_REJECT;
        return commandTemplate.execute(operation, new ServiceOperation<>() {
            private TenantId tenantId;
            private MemberId reviewerId;
            private MembershipRequestId id;

            @Override
            public void validate() {
                tenantId = organizationAccessGuard.requireActiveMembership(caller, ORGANIZATION_WRITE);
                reviewerId = memberId(caller);
                id = requiredMembershipRequestId(requestId);
            }

            @Override
            public Void execute() {
                if (approve) {
                    lockDepartmentStructure(tenantId);
                }
                MembershipRequest membershipRequest = membershipRequestRepository.findByIdForTarget(tenantId, id)
                        .orElseThrow(() -> applicationError(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                                "Membership request does not exist"));
                if (approve) {
                    approveRequest(tenantId, reviewerId, membershipRequest);
                } else {
                    membershipRequest.reject(reviewerId);
                    membershipRequestRepository.save(membershipRequest);
                }
                return null;
            }
        });
    }

    private void approveRequest(TenantId tenantId, MemberId reviewerId, MembershipRequest request) {
        if (memberProfileRepository.findById(request.getApplicantId()).filter(MemberProfile::isActive).isEmpty()) {
            throw applicationError(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "An inactive member profile cannot be approved");
        }
        requireActiveParent(tenantId, request.getTargetDepartmentId());
        OrganizationMembership membership = membershipRepository.findByMember(tenantId, request.getApplicantId())
                .map(existing -> resumeMembership(existing, request.getTargetDepartmentId()))
                .orElseGet(() -> OrganizationMembership.activate(
                        tenantId, request.getApplicantId(), request.getTargetDepartmentId()));
        membershipRepository.save(tenantId, membership);
        systemRoleProvisioner.provisionMember(tenantId, request.getApplicantId());
        authorizationChangeNotifier.changed(tenantId);
        request.approve(reviewerId);
        membershipRequestRepository.save(request);
    }

    private OrganizationMembership resumeMembership(OrganizationMembership membership, DepartmentId departmentId) {
        if (membership.isActive()) {
            throw applicationError(ApplicationErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Applicant already has active membership");
        }
        membership.resume();
        membership.assignDepartment(departmentId);
        return membership;
    }

    private MemberProfile updateProfile(MemberProfile profile, String displayName, String email, String phoneNumber) {
        profile.updateContact(displayName, optionalEmail(email), optionalPhone(phoneNumber));
        return profile;
    }

    private Department findDepartment(TenantId tenantId, DepartmentId departmentId) {
        return departmentRepository.findById(tenantId, departmentId)
                .orElseThrow(() -> applicationError(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "Department does not exist"));
    }

    private Department findVisibleDepartment(TenantId tenantId, DepartmentId departmentId) {
        Department department = findDepartment(tenantId, departmentId);
        if (department.getStatus() == io.github.archetom.crm.domain.model.DepartmentStatus.DELETED) {
            throw applicationError(ApplicationErrorCode.RESOURCE_NOT_FOUND, "Department does not exist");
        }
        return department;
    }

    private void requireActiveParent(TenantId tenantId, DepartmentId parentId) {
        if (parentId == null) return;
        Department parent = findDepartment(tenantId, parentId);
        if (!parent.isActive()) {
            throw applicationError(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "The parent department is not active");
        }
    }

    private void requireValidMoveParent(TenantId tenantId, Department department, DepartmentId parentId) {
        if (parentId == null) return;
        requireActiveParent(tenantId, parentId);
        Set<Long> visited = new HashSet<>();
        DepartmentId cursor = parentId;
        while (cursor != null) {
            if (department.getId().sameValueAs(cursor)) {
                throw applicationError(ApplicationErrorCode.DOMAIN_RULE_VIOLATION,
                        "A department cannot be moved beneath itself");
            }
            if (!visited.add(cursor.getValue())) {
                throw applicationError(ApplicationErrorCode.DOMAIN_RULE_VIOLATION,
                        "The department hierarchy contains a cycle");
            }
            cursor = findDepartment(tenantId, cursor).getParentId();
        }
    }

    private void lockDepartmentStructure(TenantId tenantId) {
        if (!organizationTenantRepository.lockForStructureChanges(tenantId)) {
            throw applicationError(ApplicationErrorCode.RESOURCE_NOT_FOUND, "The tenant is not initialized");
        }
    }

    private void rejectIfDepartmentCannotDeactivate(TenantId tenantId, DepartmentId id) {
        if (departmentRepository.existsActiveChild(tenantId, id)
                || membershipRepository.existsActiveInDepartment(tenantId, id)) {
            throw applicationError(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Departments with active children or members cannot be deactivated");
        }
    }

    private void rejectIfDepartmentCannotDelete(TenantId tenantId, DepartmentId id) {
        if (departmentRepository.existsChild(tenantId, id)
                || membershipRepository.existsInDepartment(tenantId, id)) {
            throw applicationError(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Departments with children or member assignments cannot be deleted");
        }
    }

    private MemberId memberId(AuthenticatedCaller caller) {
        return new MemberId(caller.actorId());
    }

    private DepartmentId requiredDepartmentId(Long value) {
        if (value == null || value <= 0) {
            throw applicationError(ApplicationErrorCode.PARAMETER_INVALID, "Department ID must be positive");
        }
        return new DepartmentId(value);
    }

    private DepartmentId optionalDepartmentId(Long value) {
        return value == null ? null : requiredDepartmentId(value);
    }

    private List<DepartmentId> requiredDepartmentIds(List<Long> values) {
        if (values == null || values.isEmpty() || values.size() > QueryRequest.MAX_SIZE) {
            throw applicationError(ApplicationErrorCode.PARAMETER_INVALID,
                    "Between one and 200 department IDs are required");
        }
        LinkedHashSet<DepartmentId> ids = new LinkedHashSet<>();
        for (Long value : values) {
            ids.add(requiredDepartmentId(value));
        }
        return List.copyOf(ids);
    }

    private TenantId requiredTenantId(Long value) {
        if (value == null || value <= 0) {
            throw applicationError(ApplicationErrorCode.PARAMETER_INVALID, "Target tenant ID must be positive");
        }
        return new TenantId(value);
    }

    private MembershipRequestId requiredMembershipRequestId(Long value) {
        if (value == null || value <= 0) {
            throw applicationError(ApplicationErrorCode.PARAMETER_INVALID, "Membership request ID must be positive");
        }
        return new MembershipRequestId(value);
    }

    private void requireRequest(Object request, String description) {
        if (request == null) {
            throw applicationError(ApplicationErrorCode.PARAMETER_INVALID, description + " must not be empty");
        }
    }

    private void validatePageRequest(QueryRequest request, String description) {
        requireRequest(request, description);
        if (request.getPage() == null) {
            request.setPage(QueryRequest.DEFAULT_PAGE);
        } else if (request.getPage() < 1 || request.getPage() > QueryRequest.MAX_PAGE) {
            throw applicationError(ApplicationErrorCode.PARAMETER_INVALID, "Page is outside the supported range");
        }
        if (request.getSize() == null) {
            request.setSize(QueryRequest.DEFAULT_SIZE);
        } else if (request.getSize() < 1 || request.getSize() > QueryRequest.MAX_SIZE) {
            throw applicationError(ApplicationErrorCode.PARAMETER_INVALID, "Page size is outside the supported range");
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String normalized = keyword.trim();
        if (normalized.length() > 100) {
            throw applicationError(ApplicationErrorCode.PARAMETER_INVALID,
                    "Member search keyword must not exceed 100 characters");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private Email optionalEmail(String email) {
        return email == null || email.isBlank() ? null : new Email(email);
    }

    private PhoneNumber optionalPhone(String phoneNumber) {
        return phoneNumber == null || phoneNumber.isBlank() ? null : new PhoneNumber(phoneNumber);
    }

    private NonRetryableApplicationException applicationError(ApplicationErrorCode code, String message) {
        return new NonRetryableApplicationException(code, message);
    }
}
