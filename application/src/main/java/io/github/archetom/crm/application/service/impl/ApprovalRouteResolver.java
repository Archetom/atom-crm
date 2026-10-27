package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.domain.entity.ApprovalDefinition;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.MemberProfile;
import io.github.archetom.crm.domain.entity.TenantRole;
import io.github.archetom.crm.domain.model.ApprovalFallback;
import io.github.archetom.crm.domain.model.ApprovalStepDefinition;
import io.github.archetom.crm.domain.model.ApprovalStepType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.ResolvedApprovalStep;
import io.github.archetom.crm.domain.policy.ApprovalConditionEvaluator;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.MemberProfileRepository;
import io.github.archetom.crm.domain.repository.RoleAssignmentRepository;
import io.github.archetom.crm.domain.repository.TenantRoleRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class ApprovalRouteResolver {

    private static final int MAX_ROLE_MEMBERS = ApprovalStepDefinition.MAX_ACTORS;

    private final RoleAssignmentRepository roleAssignmentRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final TenantRoleRepository roleRepository;

    List<ResolvedApprovalStep> resolve(
            TenantId tenantId,
            ApprovalDefinition definition,
            Map<FieldCode, List<String>> facts,
            Map<FieldCode, CustomFieldType> fieldTypes,
            Map<String, List<Long>> selectedApprovers) {
        Map<String, List<MemberId>> selected = selected(selectedApprovers);
        Set<String> selectableCodes = definition.getSteps().stream()
                .filter(step -> step.type() == ApprovalStepType.APPLICANT_SELECT)
                .map(ApprovalStepDefinition::code).collect(Collectors.toSet());
        if (!selectableCodes.containsAll(selected.keySet())) {
            throw error("Selected approval step is unavailable");
        }
        List<ResolvedApprovalStep> route = new ArrayList<>();
        for (ApprovalStepDefinition step : definition.getSteps()) {
            if (!ApprovalConditionEvaluator.matches(step.conditions(), facts, fieldTypes)) {
                continue;
            }
            List<MemberId> assignees = switch (step.type()) {
                case MEMBER -> active(tenantId, step.memberIds());
                case ROLE -> roleMembers(tenantId, step.roleIds());
                case APPLICANT_SELECT -> selectedActive(tenantId,
                        selected.getOrDefault(step.code(), List.of()));
            };
            if (assignees.isEmpty() && step.fallback() == ApprovalFallback.MANAGERS) {
                assignees = active(tenantId, definition.getManagerMemberIds());
            }
            if (assignees.isEmpty()) {
                if (step.fallback() == ApprovalFallback.MANAGERS) {
                    throw error("Approval manager fallback has no active member");
                }
                continue;
            }
            route.add(new ResolvedApprovalStep(step.code(), step.name(), step.order(),
                    step.mode(), assignees));
        }
        return List.copyOf(route);
    }

    private List<MemberId> roleMembers(TenantId tenantId, Collection<RoleId> roleIds) {
        Set<RoleId> activeRoles = roleRepository.findByIds(tenantId, roleIds).stream()
                .filter(TenantRole::isActive).map(TenantRole::getId)
                .collect(Collectors.toSet());
        if (activeRoles.isEmpty()) {
            return List.of();
        }
        List<MemberId> members = roleAssignmentRepository.findActiveMembersByRoles(
                tenantId, activeRoles, MAX_ROLE_MEMBERS + 1);
        if (members.size() > ApprovalStepDefinition.MAX_ACTORS) {
            throw error("Approval role route has too many members");
        }
        return members;
    }

    private List<MemberId> active(TenantId tenantId, Collection<MemberId> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<MemberId> unique = new LinkedHashSet<>(requested);
        Set<MemberId> active = membershipRepository.findByMembers(tenantId, unique).stream()
                .filter(OrganizationMembership::isActive)
                .map(OrganizationMembership::getMemberId).collect(Collectors.toSet());
        return unique.stream().filter(active::contains)
                .filter(member -> memberProfileRepository.findById(member)
                        .filter(MemberProfile::isActive).isPresent())
                .toList();
    }

    /** Submission must re-check a candidate even when it was returned moments earlier. */
    private List<MemberId> selectedActive(TenantId tenantId, Collection<MemberId> requested) {
        List<MemberId> active = active(tenantId, requested);
        if (requested != null && !active.containsAll(requested)) {
            throw new NonRetryableApplicationException(
                    ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Selected approval member is no longer eligible");
        }
        return active;
    }

    private Map<String, List<MemberId>> selected(Map<String, List<Long>> values) {
        if (values == null) {
            return Map.of();
        }
        LinkedHashMap<String, List<MemberId>> result = new LinkedHashMap<>();
        values.forEach((code, ids) -> {
            if (code == null || code.isBlank() || ids == null) {
                throw error("Selected approval members are invalid");
            }
            if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
                throw error("Selected approval members are invalid");
            }
            LinkedHashSet<MemberId> members = ids.stream().map(MemberId::new)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (members.size() != ids.size()) {
                throw error("Selected approval members must be unique");
            }
            if (result.putIfAbsent(code.trim(), List.copyOf(members)) != null) {
                throw error("Selected approval step is duplicated");
            }
        });
        return java.util.Collections.unmodifiableMap(result);
    }

    private NonRetryableApplicationException error(String message) {
        return new NonRetryableApplicationException(
                ApplicationErrorCode.PARAMETER_INVALID, message);
    }
}
