package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.domain.entity.RoleAssignment;
import io.github.archetom.crm.domain.entity.TenantRole;
import io.github.archetom.crm.domain.model.SystemRoleKey;
import io.github.archetom.crm.domain.repository.RoleAssignmentRepository;
import io.github.archetom.crm.domain.repository.TenantRoleRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Installs immutable system roles and their baseline member assignments inside command transactions. */
@Component
@RequiredArgsConstructor
public class SystemRoleProvisioner {

    private final TenantRoleRepository roleRepository;
    private final RoleAssignmentRepository assignmentRepository;

    public void provisionOwner(TenantId tenantId, MemberId memberId) {
        assignIfMissing(tenantId, memberId, ensureRole(tenantId, SystemRoleKey.TENANT_MEMBER));
        assignIfMissing(tenantId, memberId, ensureRole(tenantId, SystemRoleKey.TENANT_OWNER));
    }

    public void provisionMember(TenantId tenantId, MemberId memberId) {
        assignIfMissing(tenantId, memberId, ensureRole(tenantId, SystemRoleKey.TENANT_MEMBER));
    }

    private TenantRole ensureRole(TenantId tenantId, SystemRoleKey key) {
        return roleRepository.findBySystemKey(tenantId, key)
                .orElseGet(() -> roleRepository.save(tenantId, TenantRole.createSystem(tenantId, key)));
    }

    private void assignIfMissing(TenantId tenantId, MemberId memberId, TenantRole role) {
        if (!assignmentRepository.exists(tenantId, memberId, role.getId())) {
            assignmentRepository.save(tenantId, RoleAssignment.assign(tenantId, memberId, role.getId()));
        }
    }
}
