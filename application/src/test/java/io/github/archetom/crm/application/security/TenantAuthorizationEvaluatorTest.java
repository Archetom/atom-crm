package io.github.archetom.crm.application.security;

import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.RoleAssignment;
import io.github.archetom.crm.domain.entity.RoleGrant;
import io.github.archetom.crm.domain.entity.TenantRole;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.MembershipStatus;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.RoleAssignmentRepository;
import io.github.archetom.crm.domain.repository.TenantRoleRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.MembershipId;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static io.github.archetom.crm.domain.policy.AuthorityCatalog.USERS_READ;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantAuthorizationEvaluatorTest {

    @Test
    void authorizationReadsUseOneRepeatableReadSnapshot() {
        Transactional transactional = TenantAuthorizationEvaluator.class.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertTrue(transactional.readOnly());
        assertEquals(Isolation.REPEATABLE_READ, transactional.isolation());
    }

    @Test
    void batchEvaluationIgnoresSuspendedMembersAndTightensDepartmentScopeWithoutADepartment() {
        TenantId tenantId = new TenantId(9L);
        MemberId activeMember = new MemberId(7L);
        MemberId suspendedMember = new MemberId(8L);
        OrganizationMembership active = membership(tenantId, activeMember, MembershipStatus.ACTIVE);
        OrganizationMembership suspended = membership(tenantId, suspendedMember, MembershipStatus.SUSPENDED);
        TenantRole role = TenantRole.createCustom(tenantId, "Readers", null,
                List.of(RoleGrant.withScope(USERS_READ, DataScope.DEPARTMENT_SUBTREE)));
        role.onPersisted(new RoleId(3L), 0L, LocalDateTime.now(), LocalDateTime.now());
        RoleAssignment assignment = RoleAssignment.assign(tenantId, activeMember, role.getId());
        OrganizationMembershipRepository membershipRepository = mock(OrganizationMembershipRepository.class);
        RoleAssignmentRepository assignmentRepository = mock(RoleAssignmentRepository.class);
        TenantRoleRepository roleRepository = mock(TenantRoleRepository.class);
        when(membershipRepository.findByMembers(tenantId, List.of(activeMember, suspendedMember)))
                .thenReturn(List.of(active, suspended));
        when(assignmentRepository.findByMembers(tenantId, List.of(activeMember)))
                .thenReturn(List.of(assignment));
        when(roleRepository.findByIds(tenantId, List.of(role.getId()))).thenReturn(List.of(role));

        Map<MemberId, TenantAuthorizationEvaluator.Evaluation> result =
                new TenantAuthorizationEvaluator(membershipRepository, assignmentRepository, roleRepository)
                        .evaluateAll(tenantId, List.of(activeMember, suspendedMember));

        assertTrue(result.get(activeMember).activeMember());
        assertEquals(DataScope.SELF, result.get(activeMember).effectiveGrants().getFirst().dataScope());
        assertTrue(result.get(suspendedMember).visibleMember());
        assertFalse(result.get(suspendedMember).activeMember());
        assertTrue(result.get(suspendedMember).effectiveGrants().isEmpty());
    }

    private OrganizationMembership membership(TenantId tenantId, MemberId memberId, MembershipStatus status) {
        return OrganizationMembership.reconstitute(new OrganizationMembership.Snapshot(
                new MembershipId(memberId.getValue()), tenantId, memberId, null,
                status, 0L, LocalDateTime.now(), LocalDateTime.now()));
    }
}
