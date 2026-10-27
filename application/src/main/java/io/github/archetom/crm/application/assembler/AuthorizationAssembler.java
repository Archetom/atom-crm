package io.github.archetom.crm.application.assembler;

import io.github.archetom.crm.application.vo.AuthorityCapabilityVO;
import io.github.archetom.crm.application.vo.RoleAssignmentVO;
import io.github.archetom.crm.application.vo.RoleGrantVO;
import io.github.archetom.crm.application.vo.TenantRoleVO;
import io.github.archetom.crm.domain.entity.RoleAssignment;
import io.github.archetom.crm.domain.entity.RoleGrant;
import io.github.archetom.crm.domain.entity.TenantRole;
import io.github.archetom.crm.domain.model.AuthorityDefinition;
import io.github.archetom.crm.domain.policy.RoleAssignmentPolicy;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.common.result.Pager;

import java.util.List;

/** Converts authorization domain state into application outputs. */
public final class AuthorizationAssembler {

    private AuthorizationAssembler() {
    }

    public static AuthorityCapabilityVO toVO(AuthorityDefinition definition) {
        return new AuthorityCapabilityVO(definition.code().getValue(), definition.displayName(),
                definition.supportsDataScope());
    }

    public static RoleGrantVO toVO(RoleGrant grant) {
        return new RoleGrantVO(grant.authorityCode().getValue(),
                grant.dataScope() == null ? null : grant.dataScope().name());
    }

    public static TenantRoleVO toVO(TenantRole role) {
        return new TenantRoleVO(
                role.getId() == null ? null : role.getId().getValue(),
                role.getName(), role.getDescription(), role.getStatus().name(),
                role.getSystemKey() == null ? null : role.getSystemKey().name(),
                RoleAssignmentPolicy.configuredGrants(role).stream().map(AuthorizationAssembler::toVO).toList(),
                role.getVersion(), role.getCreatedTime(), role.getUpdatedTime());
    }

    public static RoleAssignmentVO toVO(RoleAssignment assignment) {
        return new RoleAssignmentVO(assignment.getMemberId().getValue(),
                assignment.getRoleId().getValue(), assignment.getCreatedTime());
    }

    public static Pager<RoleAssignmentVO> toPager(PageResult<RoleAssignment> assignments) {
        Pager<RoleAssignmentVO> pager = new Pager<>();
        pager.setPageNum(assignments.page());
        pager.setPageSize(assignments.size());
        pager.setTotalNum(assignments.total());
        pager.setObjectList(assignments.items().stream().map(AuthorizationAssembler::toVO).toList());
        return pager;
    }

    public static Pager<TenantRoleVO> toPager(List<TenantRole> roles, int page, int size) {
        long offset = (long) (page - 1) * size;
        List<TenantRole> items = offset >= roles.size()
                ? List.of()
                : roles.subList((int) offset, Math.min((int) offset + size, roles.size()));
        Pager<TenantRoleVO> pager = new Pager<>();
        pager.setPageNum(page);
        pager.setPageSize(size);
        pager.setTotalNum((long) roles.size());
        pager.setObjectList(items.stream().map(AuthorizationAssembler::toVO).toList());
        return pager;
    }

    public static Pager<TenantRoleVO> toRolePager(PageResult<TenantRole> roles) {
        return toRolePager(roles.items(), (int) roles.page(), (int) roles.size(), roles.total());
    }

    public static Pager<TenantRoleVO> toRolePager(
            List<TenantRole> items, int page, int size, long total) {
        Pager<TenantRoleVO> pager = new Pager<>();
        pager.setPageNum(page);
        pager.setPageSize(size);
        pager.setTotalNum(total);
        pager.setObjectList(items.stream().map(AuthorizationAssembler::toVO).toList());
        return pager;
    }
}
