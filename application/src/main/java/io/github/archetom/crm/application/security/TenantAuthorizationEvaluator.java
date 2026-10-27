package io.github.archetom.crm.application.security;

import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.RoleAssignment;
import io.github.archetom.crm.domain.entity.RoleGrant;
import io.github.archetom.crm.domain.entity.TenantRole;
import io.github.archetom.crm.domain.policy.DataScopePolicy;
import io.github.archetom.crm.domain.policy.RoleAssignmentPolicy;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.RoleAssignmentRepository;
import io.github.archetom.crm.domain.repository.TenantRoleRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reads the relational source of truth and computes one member's effective tenant grants. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class TenantAuthorizationEvaluator {

    private final OrganizationMembershipRepository membershipRepository;
    private final RoleAssignmentRepository assignmentRepository;
    private final TenantRoleRepository roleRepository;

    public Evaluation evaluate(TenantId tenantId, MemberId memberId) {
        return evaluateAll(tenantId, List.of(memberId)).getOrDefault(memberId, Evaluation.empty());
    }

    public Map<MemberId, Evaluation> evaluateAll(TenantId tenantId, Collection<MemberId> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Map.of();
        }
        List<MemberId> requested = memberIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        Map<MemberId, OrganizationMembership> memberships = membershipRepository.findByMembers(tenantId, requested)
                .stream().collect(Collectors.toMap(OrganizationMembership::getMemberId, Function.identity()));
        List<MemberId> activeMembers = memberships.values().stream()
                .filter(OrganizationMembership::isActive)
                .map(OrganizationMembership::getMemberId)
                .toList();
        List<RoleAssignment> assignments = assignmentRepository.findByMembers(tenantId, activeMembers);
        List<RoleId> roleIds = assignments.stream().map(RoleAssignment::getRoleId).distinct().toList();
        Map<RoleId, TenantRole> activeRoles = roleRepository.findByIds(tenantId, roleIds).stream()
                .filter(TenantRole::isActive)
                .collect(Collectors.toMap(TenantRole::getId, Function.identity()));
        Map<MemberId, List<TenantRole>> rolesByMember = new LinkedHashMap<>();
        for (RoleAssignment assignment : assignments) {
            TenantRole role = activeRoles.get(assignment.getRoleId());
            if (role != null) {
                rolesByMember.computeIfAbsent(assignment.getMemberId(), ignored -> new java.util.ArrayList<>())
                        .add(role);
            }
        }
        Map<MemberId, Evaluation> result = new LinkedHashMap<>();
        for (MemberId memberId : requested) {
            OrganizationMembership membership = memberships.get(memberId);
            List<TenantRole> roles = membership != null && membership.isActive()
                    ? rolesByMember.getOrDefault(memberId, List.of()) : List.of();
            List<RoleGrant> grants = roles.stream()
                    .flatMap(role -> RoleAssignmentPolicy.effectiveGrants(role).stream())
                    .map(grant -> restrictScope(grant, membership))
                    .toList();
            result.put(memberId, new Evaluation(membership, roles, DataScopePolicy.mergeGrants(grants)));
        }
        return Map.copyOf(result);
    }

    private RoleGrant restrictScope(RoleGrant grant, OrganizationMembership membership) {
        if (grant.dataScope() == null) {
            return grant;
        }
        return new RoleGrant(grant.authorityCode(),
                DataScopePolicy.restrictToMembership(grant.dataScope(), membership.getDepartmentId()));
    }

    public record Evaluation(
            OrganizationMembership membership,
            List<TenantRole> roles,
            List<RoleGrant> effectiveGrants
    ) {
        public Evaluation {
            roles = roles == null ? List.of() : List.copyOf(roles);
            effectiveGrants = effectiveGrants == null ? List.of() : List.copyOf(effectiveGrants);
        }

        public static Evaluation empty() {
            return new Evaluation(null, List.of(), List.of());
        }

        public boolean activeMember() {
            return membership != null && membership.isActive();
        }

        public boolean visibleMember() {
            return membership != null;
        }
    }
}
