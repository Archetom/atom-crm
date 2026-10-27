package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.domain.entity.TenantRole;
import io.github.archetom.crm.domain.model.SystemRoleKey;
import io.github.archetom.crm.domain.repository.RoleAssignmentRepository;
import io.github.archetom.crm.domain.repository.TenantRoleRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemRoleProvisionerTest {

    @Test
    void ownerProvisioningKeepsMemberBaselineAndAddsOwner() {
        TenantId tenantId = new TenantId(9L);
        MemberId memberId = new MemberId(7L);
        TenantRole memberRole = systemRole(tenantId, SystemRoleKey.TENANT_MEMBER, 1L);
        TenantRole ownerRole = systemRole(tenantId, SystemRoleKey.TENANT_OWNER, 2L);
        TenantRoleRepository roleRepository = mock(TenantRoleRepository.class);
        RoleAssignmentRepository assignmentRepository = mock(RoleAssignmentRepository.class);
        when(roleRepository.findBySystemKey(tenantId, SystemRoleKey.TENANT_MEMBER))
                .thenReturn(Optional.of(memberRole));
        when(roleRepository.findBySystemKey(tenantId, SystemRoleKey.TENANT_OWNER))
                .thenReturn(Optional.of(ownerRole));

        new SystemRoleProvisioner(roleRepository, assignmentRepository).provisionOwner(tenantId, memberId);

        verify(assignmentRepository).save(argThat(tenantId::sameValueAs), argThat(assignment ->
                assignment.getMemberId().sameValueAs(memberId)
                        && assignment.getRoleId().sameValueAs(memberRole.getId())));
        verify(assignmentRepository).save(argThat(tenantId::sameValueAs), argThat(assignment ->
                assignment.getMemberId().sameValueAs(memberId)
                        && assignment.getRoleId().sameValueAs(ownerRole.getId())));
    }

    private TenantRole systemRole(TenantId tenantId, SystemRoleKey key, Long id) {
        TenantRole role = TenantRole.createSystem(tenantId, key);
        role.onPersisted(new RoleId(id), 0L, LocalDateTime.now(), LocalDateTime.now());
        return role;
    }
}
