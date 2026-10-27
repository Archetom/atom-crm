package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.dto.response.AuthorityCapabilityResponse;
import io.github.archetom.crm.api.dto.response.MemberAuthorizationResponse;
import io.github.archetom.crm.api.dto.response.RoleAssignmentResponse;
import io.github.archetom.crm.api.dto.response.RoleGrantResponse;
import io.github.archetom.crm.api.dto.response.TenantRoleResponse;
import io.github.archetom.crm.application.vo.AuthorityCapabilityVO;
import io.github.archetom.crm.application.vo.MemberAuthorizationVO;
import io.github.archetom.crm.application.vo.RoleAssignmentVO;
import io.github.archetom.crm.application.vo.RoleGrantVO;
import io.github.archetom.crm.application.vo.TenantRoleVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.common.result.Pager;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Maps authorization application outputs to public response contracts. */
final class AuthorizationResponseAssembler {

    private AuthorizationResponseAssembler() {
    }

    static AuthorityCapabilityResponse toResponse(AuthorityCapabilityVO source) {
        return new AuthorityCapabilityResponse().setCode(source.code()).setDisplayName(source.displayName())
                .setSupportsDataScope(source.supportsDataScope());
    }

    static RoleGrantResponse toResponse(RoleGrantVO source) {
        return new RoleGrantResponse().setAuthorityCode(source.authorityCode()).setDataScope(source.dataScope());
    }

    static TenantRoleResponse toResponse(TenantRoleVO source) {
        return new TenantRoleResponse().setId(source.id()).setName(source.name()).setDescription(source.description())
                .setStatus(source.status()).setSystemKey(source.systemKey()).setVersion(source.version())
                .setCreatedTime(source.createdTime()).setUpdatedTime(source.updatedTime())
                .setGrants(source.grants().stream().map(AuthorizationResponseAssembler::toResponse).toList());
    }

    static RoleAssignmentResponse toResponse(RoleAssignmentVO source) {
        return new RoleAssignmentResponse().setActorId(source.actorId()).setRoleId(source.roleId())
                .setCreatedTime(source.createdTime());
    }

    static MemberAuthorizationResponse toResponse(MemberAuthorizationVO source) {
        return new MemberAuthorizationResponse().setActorId(source.actorId())
                .setRoles(source.roles().stream().map(AuthorizationResponseAssembler::toResponse).toList())
                .setAuthorities(source.authorities()).setDataScopes(source.dataScopes());
    }

    static List<AuthorityCapabilityResponse> toCapabilityResponses(List<AuthorityCapabilityVO> source) {
        return source.stream().map(AuthorizationResponseAssembler::toResponse).toList();
    }

    static List<MemberAuthorizationResponse> toMemberResponses(List<MemberAuthorizationVO> source) {
        return source.stream().map(AuthorizationResponseAssembler::toResponse).toList();
    }

    static Map<Long, List<TenantRoleResponse>> toMemberRoleResponses(Map<Long, List<TenantRoleVO>> source) {
        Map<Long, List<TenantRoleResponse>> result = new LinkedHashMap<>();
        source.forEach((actorId, roles) -> result.put(actorId,
                roles.stream().map(AuthorizationResponseAssembler::toResponse).toList()));
        return Map.copyOf(result);
    }

    static Pager<TenantRoleResponse> toRolePager(Pager<TenantRoleVO> source) {
        Pager<TenantRoleResponse> pager = PageUtil.copy(source);
        pager.setMeta(null);
        pager.setObjectList(source.getObjectList().stream().map(AuthorizationResponseAssembler::toResponse).toList());
        return pager;
    }

    static Pager<RoleAssignmentResponse> toAssignmentPager(Pager<RoleAssignmentVO> source) {
        Pager<RoleAssignmentResponse> pager = PageUtil.copy(source);
        pager.setMeta(null);
        pager.setObjectList(source.getObjectList().stream().map(AuthorizationResponseAssembler::toResponse).toList());
        return pager;
    }
}
