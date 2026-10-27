package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.domain.entity.ApprovalDefinition;
import io.github.archetom.crm.domain.entity.TenantRole;
import io.github.archetom.crm.domain.model.ApprovalFallback;
import io.github.archetom.crm.domain.model.ApprovalRepeatMode;
import io.github.archetom.crm.domain.model.ApprovalStepDefinition;
import io.github.archetom.crm.domain.model.ApprovalStepMode;
import io.github.archetom.crm.domain.model.ApprovalStepType;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.MemberProfileRepository;
import io.github.archetom.crm.domain.repository.RoleAssignmentRepository;
import io.github.archetom.crm.domain.repository.TenantRoleRepository;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalRouteResolverTest {

    private static final TenantId TENANT = new TenantId(99L);
    private static final RoleId ROLE = new RoleId(12L);

    @Mock private RoleAssignmentRepository roleAssignmentRepository;
    @Mock private OrganizationMembershipRepository membershipRepository;
    @Mock private MemberProfileRepository memberProfileRepository;
    @Mock private TenantRoleRepository roleRepository;

    @Test
    void inactiveRoleDoesNotResolveItsRetainedAssignments() {
        TenantRole inactiveRole = TenantRole.createCustom(
                TENANT, "Former reviewers", null, List.of());
        LocalDateTime now = LocalDateTime.now();
        inactiveRole.onPersisted(ROLE, 0L, now, now);
        inactiveRole.deactivate();
        ApprovalDefinition definition = ApprovalDefinition.create(TENANT,
                BusinessObjectType.CUSTOMER, "Role approval", null,
                ApprovalRepeatMode.ONCE, List.of(), List.of(), List.of(),
                List.of(new ApprovalStepDefinition("review", "Review", 0,
                        ApprovalStepType.ROLE, ApprovalStepMode.ALL,
                        ApprovalFallback.AUTO_APPROVE, List.of(), List.of(ROLE), List.of())));
        when(roleRepository.findByIds(TENANT, List.of(ROLE)))
                .thenReturn(List.of(inactiveRole));
        ApprovalRouteResolver resolver = new ApprovalRouteResolver(
                roleAssignmentRepository, membershipRepository, memberProfileRepository, roleRepository);

        var route = resolver.resolve(TENANT, definition,
                Map.of(), Map.of(), Map.of());

        assertTrue(route.isEmpty());
        verifyNoInteractions(roleAssignmentRepository, membershipRepository);
    }

    @Test
    void normalizedSelectedStepCodesCannotCollide() {
        ApprovalDefinition definition = ApprovalDefinition.create(TENANT,
                BusinessObjectType.CUSTOMER, "Selected approval", null,
                ApprovalRepeatMode.ONCE, List.of(), List.of(), List.of(),
                List.of(new ApprovalStepDefinition("review", "Review", 0,
                        ApprovalStepType.APPLICANT_SELECT, ApprovalStepMode.ALL,
                        ApprovalFallback.AUTO_APPROVE, List.of(), List.of(), List.of())));
        ApprovalRouteResolver resolver = new ApprovalRouteResolver(
                roleAssignmentRepository, membershipRepository, memberProfileRepository, roleRepository);

        assertThrows(io.github.archetom.crm.shared.exception.NonRetryableApplicationException.class,
                () -> resolver.resolve(TENANT, definition, Map.of(), Map.of(),
                        Map.of("review", List.of(1L), " review ", List.of(2L))));
        verifyNoInteractions(roleAssignmentRepository, membershipRepository, roleRepository);
    }

    @Test
    void mergedRoleMembersCannotExceedResolvedStepLimit() {
        RoleId secondRoleId = new RoleId(13L);
        TenantRole firstRole = activeRole(ROLE, "First reviewers");
        TenantRole secondRole = activeRole(secondRoleId, "Second reviewers");
        ApprovalDefinition definition = ApprovalDefinition.create(TENANT,
                BusinessObjectType.CUSTOMER, "Large role approval", null,
                ApprovalRepeatMode.ONCE, List.of(), List.of(), List.of(),
                List.of(new ApprovalStepDefinition("review", "Review", 0,
                        ApprovalStepType.ROLE, ApprovalStepMode.ALL,
                        ApprovalFallback.AUTO_APPROVE, List.of(),
                        List.of(ROLE, secondRoleId), List.of())));
        when(roleRepository.findByIds(TENANT, List.of(ROLE, secondRoleId)))
                .thenReturn(List.of(firstRole, secondRole));
        when(roleAssignmentRepository.findActiveMembersByRoles(
                org.mockito.ArgumentMatchers.eq(TENANT),
                org.mockito.ArgumentMatchers.argThat(ids -> ids.size() == 2
                        && ids.containsAll(List.of(ROLE, secondRoleId))),
                org.mockito.ArgumentMatchers.eq(101)))
                .thenReturn(members(1, 101));
        ApprovalRouteResolver resolver = new ApprovalRouteResolver(
                roleAssignmentRepository, membershipRepository, memberProfileRepository, roleRepository);

        assertThrows(io.github.archetom.crm.shared.exception.NonRetryableApplicationException.class,
                () -> resolver.resolve(TENANT, definition, Map.of(), Map.of(), Map.of()));
    }

    @Test
    void inactiveAssignmentsDoNotCountTowardRoleMemberLimit() {
        TenantRole role = activeRole(ROLE, "Current reviewers");
        ApprovalDefinition definition = ApprovalDefinition.create(TENANT,
                BusinessObjectType.CUSTOMER, "Bounded role approval", null,
                ApprovalRepeatMode.ONCE, List.of(), List.of(), List.of(),
                List.of(new ApprovalStepDefinition("review", "Review", 0,
                        ApprovalStepType.ROLE, ApprovalStepMode.ALL,
                        ApprovalFallback.AUTO_APPROVE, List.of(), List.of(ROLE), List.of())));
        when(roleRepository.findByIds(TENANT, List.of(ROLE))).thenReturn(List.of(role));
        when(roleAssignmentRepository.findActiveMembersByRoles(
                TENANT, java.util.Set.of(ROLE), 101))
                .thenReturn(members(1, 100));
        ApprovalRouteResolver resolver = new ApprovalRouteResolver(
                roleAssignmentRepository, membershipRepository, memberProfileRepository, roleRepository);

        var route = resolver.resolve(TENANT, definition, Map.of(), Map.of(), Map.of());

        assertEquals(100, route.getFirst().assignees().size());
        verifyNoInteractions(membershipRepository);
    }

    private TenantRole activeRole(RoleId id, String name) {
        TenantRole role = TenantRole.createCustom(TENANT, name, null, List.of());
        LocalDateTime now = LocalDateTime.now();
        role.onPersisted(id, 0L, now, now);
        return role;
    }

    private List<MemberId> members(long firstMemberId, int count) {
        return LongStream.range(firstMemberId, firstMemberId + count)
                .mapToObj(MemberId::new)
                .toList();
    }
}
