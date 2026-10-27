package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.MemberRoleBatchQueryRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.api.dto.request.RoleAssignmentCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleCopyRequest;
import io.github.archetom.crm.api.dto.request.RoleCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleGrantReplaceRequest;
import io.github.archetom.crm.api.dto.request.RoleGrantRequest;
import io.github.archetom.crm.api.dto.request.RoleQueryRequest;
import io.github.archetom.crm.api.dto.request.RoleUpdateRequest;
import io.github.archetom.crm.application.assembler.AuthorizationAssembler;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.application.security.TenantAuthorizationChangeNotifier;
import io.github.archetom.crm.application.service.AuthorizationService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.AuthorityCapabilityVO;
import io.github.archetom.crm.application.vo.MemberAuthorizationVO;
import io.github.archetom.crm.application.vo.RoleAssignmentVO;
import io.github.archetom.crm.application.vo.TenantRoleVO;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.RoleAssignment;
import io.github.archetom.crm.domain.entity.RoleGrant;
import io.github.archetom.crm.domain.entity.TenantRole;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.RoleStatus;
import io.github.archetom.crm.domain.model.SystemRoleKey;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.RoleAssignmentPolicy;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.RoleAssignmentRepository;
import io.github.archetom.crm.domain.repository.TenantRoleRepository;
import io.github.archetom.crm.domain.valueobject.AuthorityCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Application orchestration for tenant roles without trusting request-owned identity or authority. */
@Service
@RequiredArgsConstructor
public class AuthorizationServiceImpl implements AuthorizationService {

    private final TenantRoleRepository roleRepository;
    private final RoleAssignmentRepository assignmentRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationTenantRepository organizationTenantRepository;
    private final TenantAuthorizationEvaluator authorizationEvaluator;
    private final TenantAuthorizationChangeNotifier authorizationChangeNotifier;
    private final OrganizationAccessGuard accessGuard;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<MemberAuthorizationVO> getMyAuthorization(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.AUTHORIZATION_ME_GET, new ServiceOperation<>() {
            private TenantId tenantId;
            private MemberId memberId;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.NAVIGATION_READ);
                memberId = new MemberId(caller.actorId());
            }

            @Override
            public MemberAuthorizationVO execute() {
                return toMemberAuthorization(
                        memberId, authorizationEvaluator.evaluate(tenantId, memberId), caller.authorities());
            }
        });
    }

    @Override
    public Result<List<AuthorityCapabilityVO>> listCapabilities(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.AUTHORIZATION_CAPABILITY_LIST, new ServiceOperation<>() {
            @Override
            public void validate() {
                accessGuard.requireActiveMembership(caller, AuthorityCatalog.AUTHORIZATION_READ);
            }

            @Override
            public List<AuthorityCapabilityVO> execute() {
                return AuthorityCatalog.tenantAssignable().stream().map(AuthorizationAssembler::toVO).toList();
            }
        });
    }

    @Override
    public Result<Pager<TenantRoleVO>> queryRoles(AuthenticatedCaller caller, RoleQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.AUTHORIZATION_ROLE_QUERY, new ServiceOperation<>() {
            private TenantId tenantId;
            private int page;
            private int size;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.AUTHORIZATION_READ);
                validatePage(request, "Role query");
                page = request.getPage();
                size = request.getSize();
            }

            @Override
            public Pager<TenantRoleVO> execute() {
                RoleStatus status = parseStatus(request.getStatus());
                String keyword = normalizeKeyword(request.getKeyword());
                if (!Boolean.TRUE.equals(request.getAssignableOnly())) {
                    return AuthorizationAssembler.toRolePager(
                            roleRepository.findPage(tenantId, status, keyword, page, size));
                }
                if (!caller.hasAuthority(AuthorityCatalog.AUTHORIZATION_ASSIGN)) {
                    return AuthorizationAssembler.toRolePager(List.of(), page, size, 0);
                }
                return queryAssignableRoles(tenantId, status, keyword, page, size,
                        callerGrants(caller, tenantId));
            }
        });
    }

    @Override
    public Result<TenantRoleVO> createRole(AuthenticatedCaller caller, RoleCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.AUTHORIZATION_ROLE_CREATE, new ServiceOperation<>() {
            private TenantId tenantId;
            private List<RoleGrant> callerGrants;
            private TenantRole role;

            @Override
            public void validate() {
                tenantId = requireWriteAndAssign(caller);
                require(request, "Role create request");
            }

            @Override
            public TenantRoleVO execute() {
                lockTenant(tenantId);
                callerGrants = callerGrants(caller, tenantId);
                role = TenantRole.createCustom(tenantId, request.getName(), request.getDescription(),
                        grants(request.getGrants()));
                RoleAssignmentPolicy.requireDelegable(role, callerGrants);
                return changed(tenantId, AuthorizationAssembler.toVO(roleRepository.save(tenantId, role)));
            }
        });
    }

    @Override
    public Result<TenantRoleVO> updateRole(AuthenticatedCaller caller, Long roleId, RoleUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.AUTHORIZATION_ROLE_UPDATE, new ServiceOperation<>() {
            private TenantId tenantId;
            private RoleId id;
            private List<RoleGrant> callerGrants;

            @Override
            public void validate() {
                tenantId = requireWriteAndAssign(caller);
                id = roleId(roleId);
                require(request, "Role update request");
            }

            @Override
            public TenantRoleVO execute() {
                lockTenant(tenantId);
                callerGrants = callerGrants(caller, tenantId);
                TenantRole role = findRole(tenantId, id);
                requireVersion(role, request.getVersion());
                RoleAssignmentPolicy.requireDelegable(role, callerGrants);
                role.update(request.getName(), request.getDescription());
                RoleStatus requestedStatus = requiredStatus(request.getStatus());
                if (requestedStatus == RoleStatus.ACTIVE) {
                    role.activate();
                } else {
                    role.deactivate();
                }
                RoleAssignmentPolicy.requireDelegable(role, callerGrants);
                return changed(tenantId, AuthorizationAssembler.toVO(roleRepository.save(tenantId, role)));
            }
        });
    }

    @Override
    public Result<Void> deleteRole(AuthenticatedCaller caller, Long roleId, Long version) {
        return commandTemplate.execute(UseCaseOperation.AUTHORIZATION_ROLE_DELETE, new ServiceOperation<>() {
            private TenantId tenantId;
            private RoleId id;
            private List<RoleGrant> callerGrants;

            @Override
            public void validate() {
                tenantId = requireWriteAndAssign(caller);
                id = roleId(roleId);
                if (version == null || version < 0) {
                    throw error(ApplicationErrorCode.PARAMETER_INVALID, "Role version must not be negative");
                }
            }

            @Override
            public Void execute() {
                lockTenant(tenantId);
                callerGrants = callerGrants(caller, tenantId);
                TenantRole role = findRole(tenantId, id);
                requireVersion(role, version);
                RoleAssignmentPolicy.requireDelegable(role, callerGrants);
                role.assertDeletable(assignmentRepository.countByRole(tenantId, id) > 0);
                roleRepository.delete(tenantId, role);
                authorizationChangeNotifier.changed(tenantId);
                return null;
            }
        });
    }

    @Override
    public Result<TenantRoleVO> copyRole(AuthenticatedCaller caller, Long roleId, RoleCopyRequest request) {
        return commandTemplate.execute(UseCaseOperation.AUTHORIZATION_ROLE_COPY, new ServiceOperation<>() {
            private TenantId tenantId;
            private RoleId id;
            private List<RoleGrant> callerGrants;

            @Override
            public void validate() {
                tenantId = requireWriteAndAssign(caller);
                id = roleId(roleId);
                require(request, "Role copy request");
            }

            @Override
            public TenantRoleVO execute() {
                lockTenant(tenantId);
                callerGrants = callerGrants(caller, tenantId);
                TenantRole source = findRole(tenantId, id);
                RoleAssignmentPolicy.requireDelegable(source, callerGrants);
                TenantRole copy = source.copyAs(request.getName());
                RoleAssignmentPolicy.requireDelegable(copy, callerGrants);
                return changed(tenantId, AuthorizationAssembler.toVO(roleRepository.save(tenantId, copy)));
            }
        });
    }

    @Override
    public Result<TenantRoleVO> replaceRoleGrants(
            AuthenticatedCaller caller, Long roleId, RoleGrantReplaceRequest request) {
        return commandTemplate.execute(UseCaseOperation.AUTHORIZATION_ROLE_GRANT_REPLACE, new ServiceOperation<>() {
            private TenantId tenantId;
            private RoleId id;
            private List<RoleGrant> replacement;
            private List<RoleGrant> callerGrants;

            @Override
            public void validate() {
                tenantId = requireWriteAndAssign(caller);
                id = roleId(roleId);
                require(request, "Role grant request");
                replacement = grants(request.getGrants());
            }

            @Override
            public TenantRoleVO execute() {
                lockTenant(tenantId);
                callerGrants = callerGrants(caller, tenantId);
                TenantRole role = findRole(tenantId, id);
                requireVersion(role, request.getVersion());
                RoleAssignmentPolicy.requireDelegable(role, callerGrants);
                role.replaceGrants(replacement);
                RoleAssignmentPolicy.requireDelegable(role, callerGrants);
                return changed(tenantId, AuthorizationAssembler.toVO(roleRepository.save(tenantId, role)));
            }
        });
    }

    @Override
    public Result<Pager<RoleAssignmentVO>> listRoleAssignments(
            AuthenticatedCaller caller, Long roleId, QueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.AUTHORIZATION_ASSIGNMENT_LIST, new ServiceOperation<>() {
            private TenantId tenantId;
            private RoleId id;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.AUTHORIZATION_READ);
                id = roleId(roleId);
                validatePage(request, "Role assignment query");
            }

            @Override
            public Pager<RoleAssignmentVO> execute() {
                findRole(tenantId, id);
                return AuthorizationAssembler.toPager(assignmentRepository.findByRole(
                        tenantId, id, request.getPage(), request.getSize()));
            }
        });
    }

    @Override
    public Result<Void> assignRole(
            AuthenticatedCaller caller, Long roleId, RoleAssignmentCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.AUTHORIZATION_ASSIGNMENT_ADD, new ServiceOperation<>() {
            private TenantId tenantId;
            private RoleId id;
            private List<MemberId> memberIds;
            private List<RoleGrant> callerGrants;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.AUTHORIZATION_ASSIGN);
                id = roleId(roleId);
                require(request, "Role assignment request");
                memberIds = memberIds(request.getActorIds());
            }

            @Override
            public Void execute() {
                lockTenant(tenantId);
                callerGrants = callerGrants(caller, tenantId);
                TenantRole role = findRole(tenantId, id);
                for (MemberId memberId : memberIds) {
                    OrganizationMembership membership = membershipRepository.findByMember(tenantId, memberId)
                            .orElseThrow(() -> error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                                    "Member does not exist in this tenant"));
                    RoleAssignmentPolicy.requireAssignable(membership.isActive(), role, callerGrants);
                    assignmentRepository.save(tenantId, RoleAssignment.assign(tenantId, memberId, id));
                }
                authorizationChangeNotifier.changed(tenantId);
                return null;
            }
        });
    }

    @Override
    public Result<Void> removeRoleAssignment(AuthenticatedCaller caller, Long roleId, Long actorId) {
        return commandTemplate.execute(UseCaseOperation.AUTHORIZATION_ASSIGNMENT_REMOVE, new ServiceOperation<>() {
            private TenantId tenantId;
            private RoleId id;
            private MemberId memberId;
            private List<RoleGrant> callerGrants;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.AUTHORIZATION_ASSIGN);
                id = roleId(roleId);
                memberId = memberId(actorId);
            }

            @Override
            public Void execute() {
                lockTenant(tenantId);
                callerGrants = callerGrants(caller, tenantId);
                OrganizationMembership membership = membershipRepository.findByMember(tenantId, memberId)
                        .orElseThrow(() -> error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                                "Member does not exist in this tenant"));
                TenantRole role = findRole(tenantId, id);
                RoleAssignmentPolicy.requireDelegable(role, callerGrants);
                if (!assignmentRepository.exists(tenantId, memberId, id)) {
                    throw error(ApplicationErrorCode.RESOURCE_NOT_FOUND, "Role assignment does not exist");
                }
                long activeOwnerCount = role.getSystemKey() == SystemRoleKey.TENANT_OWNER
                        && membership.isActive()
                        ? assignmentRepository.countActiveMembersByRole(tenantId, id) : 0;
                RoleAssignmentPolicy.requireRemovable(role, membership.isActive(), activeOwnerCount);
                if (!assignmentRepository.delete(tenantId, memberId, id)) {
                    throw error(ApplicationErrorCode.RESOURCE_NOT_FOUND, "Role assignment does not exist");
                }
                authorizationChangeNotifier.changed(tenantId);
                return null;
            }
        });
    }

    @Override
    public Result<MemberAuthorizationVO> getMemberRoles(AuthenticatedCaller caller, Long actorId) {
        return queryTemplate.execute(UseCaseOperation.AUTHORIZATION_MEMBER_ROLE_GET, new ServiceOperation<>() {
            private TenantId tenantId;
            private MemberId memberId;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.AUTHORIZATION_READ);
                memberId = memberId(actorId);
            }

            @Override
            public MemberAuthorizationVO execute() {
                requireVisibleMembership(tenantId, memberId);
                return toMemberAuthorization(memberId, authorizationEvaluator.evaluate(tenantId, memberId));
            }
        });
    }

    @Override
    public Result<Map<Long, List<TenantRoleVO>>> getMembersRoles(
            AuthenticatedCaller caller, MemberRoleBatchQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.AUTHORIZATION_MEMBER_ROLE_BATCH_GET, new ServiceOperation<>() {
            private TenantId tenantId;
            private List<MemberId> memberIds;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.AUTHORIZATION_READ);
                require(request, "Member role query");
                memberIds = memberIds(request.getActorIds());
            }

            @Override
            public Map<Long, List<TenantRoleVO>> execute() {
                Map<MemberId, TenantAuthorizationEvaluator.Evaluation> evaluations =
                        authorizationEvaluator.evaluateAll(tenantId, memberIds);
                Map<Long, List<TenantRoleVO>> result = new LinkedHashMap<>();
                for (MemberId memberId : memberIds) {
                    TenantAuthorizationEvaluator.Evaluation evaluation = evaluations.get(memberId);
                    if (evaluation == null || !evaluation.visibleMember()) {
                        throw error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                                "Member does not exist in this tenant");
                    }
                    result.put(memberId.getValue(), evaluation.roles()
                            .stream().map(AuthorizationAssembler::toVO).toList());
                }
                return Map.copyOf(result);
            }
        });
    }

    private List<RoleGrant> callerGrants(AuthenticatedCaller caller, TenantId tenantId) {
        return authorizationEvaluator.evaluate(tenantId, new MemberId(caller.actorId())).effectiveGrants();
    }

    private Pager<TenantRoleVO> queryAssignableRoles(
            TenantId tenantId, RoleStatus status, String keyword, int page, int size,
            List<RoleGrant> callerGrants) {
        long offset = (long) (page - 1) * size;
        long assignableTotal = 0;
        List<TenantRole> requestedItems = new java.util.ArrayList<>(size);
        int scanPage = 1;
        while (true) {
            PageResult<TenantRole> candidates = roleRepository.findPage(
                    tenantId, status, keyword, scanPage, QueryRequest.MAX_SIZE);
            for (TenantRole role : candidates.items()) {
                if (!role.isActive() || !RoleAssignmentPolicy.canDelegate(role, callerGrants)) {
                    continue;
                }
                if (assignableTotal >= offset && requestedItems.size() < size) {
                    requestedItems.add(role);
                }
                assignableTotal++;
            }
            if (candidates.items().isEmpty()
                    || (long) scanPage * QueryRequest.MAX_SIZE >= candidates.total()) {
                break;
            }
            scanPage++;
        }
        return AuthorizationAssembler.toRolePager(requestedItems, page, size, assignableTotal);
    }

    private MemberAuthorizationVO toMemberAuthorization(
            MemberId memberId, TenantAuthorizationEvaluator.Evaluation evaluation) {
        return toMemberAuthorization(memberId, evaluation, Set.of());
    }

    private MemberAuthorizationVO toMemberAuthorization(
            MemberId memberId, TenantAuthorizationEvaluator.Evaluation evaluation,
            Collection<String> callerAuthorities) {
        Set<String> authorities = new java.util.TreeSet<>(callerAuthorities);
        Map<String, String> scopes = new java.util.TreeMap<>();
        for (RoleGrant grant : evaluation.effectiveGrants()) {
            authorities.add(grant.authorityCode().getValue());
            if (grant.dataScope() != null) {
                scopes.put(grant.authorityCode().getValue(), grant.dataScope().name());
            }
        }
        return new MemberAuthorizationVO(memberId.getValue(),
                evaluation.roles().stream().map(AuthorizationAssembler::toVO).toList(),
                Set.copyOf(authorities), Map.copyOf(scopes));
    }

    private TenantRole findRole(TenantId tenantId, RoleId roleId) {
        return roleRepository.findById(tenantId, roleId)
                .orElseThrow(() -> error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "Role does not exist"));
    }

    private void requireVisibleMembership(TenantId tenantId, MemberId memberId) {
        if (membershipRepository.findByMember(tenantId, memberId).isEmpty()) {
            throw error(ApplicationErrorCode.RESOURCE_NOT_FOUND, "Member does not exist in this tenant");
        }
    }

    private List<RoleGrant> grants(Collection<RoleGrantRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        if (requests.size() > QueryRequest.MAX_SIZE) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, "Role grant limit exceeded");
        }
        return requests.stream().map(request -> {
            if (request == null) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID, "Role grant must not be empty");
            }
            DataScope scope = request.getDataScope() == null || request.getDataScope().isBlank()
                    ? null : parseScope(request.getDataScope());
            return new RoleGrant(new AuthorityCode(request.getAuthorityCode()), scope);
        }).toList();
    }

    private List<MemberId> memberIds(Collection<Long> actorIds) {
        if (actorIds == null || actorIds.isEmpty() || actorIds.size() > QueryRequest.MAX_SIZE) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, "Between one and 200 actor IDs are required");
        }
        LinkedHashSet<MemberId> ids = new LinkedHashSet<>();
        for (Long actorId : actorIds) {
            ids.add(memberId(actorId));
        }
        return List.copyOf(ids);
    }

    private MemberId memberId(Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, "Actor ID must be positive");
        }
        return new MemberId(actorId);
    }

    private RoleId roleId(Long value) {
        if (value == null || value <= 0) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, "Role ID must be positive");
        }
        return new RoleId(value);
    }

    private RoleStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return RoleStatus.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, "Role status is invalid");
        }
    }

    private RoleStatus requiredStatus(String value) {
        RoleStatus status = parseStatus(value);
        if (status == null) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, "Role status is required");
        }
        return status;
    }

    private DataScope parseScope(String value) {
        try {
            return DataScope.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, "Data scope is invalid");
        }
    }

    private void requireVersion(TenantRole role, Long version) {
        if (version == null || !version.equals(role.getVersion())) {
            throw error(ApplicationErrorCode.VERSION_CONFLICT, "Role version is stale");
        }
    }

    private void validatePage(QueryRequest request, String description) {
        require(request, description);
        if (request.getPage() == null) {
            request.setPage(QueryRequest.DEFAULT_PAGE);
        }
        if (request.getSize() == null) {
            request.setSize(QueryRequest.DEFAULT_SIZE);
        }
        if (request.getPage() < 1 || request.getPage() > QueryRequest.MAX_PAGE
                || request.getSize() < 1 || request.getSize() > QueryRequest.MAX_SIZE) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, "Page request is outside the supported range");
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > 100) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, "Role keyword is too long");
        }
        return normalized;
    }

    private void require(Object request, String description) {
        if (request == null) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, description + " must not be empty");
        }
    }

    private TenantId requireWriteAndAssign(AuthenticatedCaller caller) {
        TenantId tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.AUTHORIZATION_WRITE);
        if (!caller.hasAuthority(AuthorityCatalog.AUTHORIZATION_ASSIGN)) {
            throw error(ApplicationErrorCode.ACCESS_DENIED,
                    "Caller does not have the required assignment authority");
        }
        return tenantId;
    }

    private <T> T changed(TenantId tenantId, T result) {
        authorizationChangeNotifier.changed(tenantId);
        return result;
    }

    private void lockTenant(TenantId tenantId) {
        if (!organizationTenantRepository.lockForTenantChanges(tenantId)) {
            throw error(ApplicationErrorCode.RESOURCE_NOT_FOUND, "The tenant is not initialized");
        }
    }

    private NonRetryableApplicationException error(ApplicationErrorCode code, String message) {
        return new NonRetryableApplicationException(code, message);
    }
}
