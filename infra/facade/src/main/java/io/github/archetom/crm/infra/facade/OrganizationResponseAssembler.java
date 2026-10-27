package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.dto.response.DepartmentResponse;
import io.github.archetom.crm.api.dto.response.DepartmentMemberProfileResponse;
import io.github.archetom.crm.api.dto.response.MemberProfileResponse;
import io.github.archetom.crm.api.dto.response.MembershipRequestResponse;
import io.github.archetom.crm.api.dto.response.OrganizationMembershipResponse;
import io.github.archetom.crm.api.dto.response.TenantAccessResponse;
import io.github.archetom.crm.application.vo.DepartmentVO;
import io.github.archetom.crm.application.vo.DepartmentMemberProfileVO;
import io.github.archetom.crm.application.vo.MemberProfileVO;
import io.github.archetom.crm.application.vo.MembershipRequestVO;
import io.github.archetom.crm.application.vo.OrganizationMembershipVO;
import io.github.archetom.crm.application.vo.TenantAccessVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.common.result.Pager;

import java.util.List;

/** Converts application outputs into versioned public API contracts. */
final class OrganizationResponseAssembler {

    private OrganizationResponseAssembler() {
    }

    static DepartmentResponse toResponse(DepartmentVO source) {
        return new DepartmentResponse().setId(source.getId()).setParentDepartmentId(source.getParentDepartmentId())
                .setName(source.getName()).setStatus(source.getStatus()).setActive(source.isActive());
    }

    static MemberProfileResponse toResponse(MemberProfileVO source) {
        return new MemberProfileResponse().setActorId(source.getActorId()).setDisplayName(source.getDisplayName())
                .setEmail(source.getEmail()).setMaskedPhoneNumber(source.getMaskedPhoneNumber())
                .setActive(source.isActive());
    }

    static DepartmentMemberProfileResponse toResponse(DepartmentMemberProfileVO source) {
        return new DepartmentMemberProfileResponse().setActorId(source.getActorId())
                .setDisplayName(source.getDisplayName()).setEmail(source.getEmail())
                .setMaskedPhoneNumber(source.getMaskedPhoneNumber()).setActive(source.isActive())
                .setDepartmentName(source.getDepartmentName()).setLoginName(source.getLoginName())
                .setRoleNames(source.getRoleNames());
    }

    static OrganizationMembershipResponse toResponse(OrganizationMembershipVO source) {
        return new OrganizationMembershipResponse().setId(source.getId()).setTenantId(source.getTenantId())
                .setActorId(source.getActorId()).setDepartmentId(source.getDepartmentId())
                .setStatus(source.getStatus()).setActive(source.isActive());
    }

    static MembershipRequestResponse toResponse(MembershipRequestVO source) {
        return new MembershipRequestResponse().setId(source.getId()).setApplicantActorId(source.getApplicantActorId())
                .setTargetTenantId(source.getTargetTenantId()).setTargetDepartmentId(source.getTargetDepartmentId())
                .setMessage(source.getMessage()).setStatus(source.getStatus()).setReviewerActorId(source.getReviewerActorId())
                .setCreatedTime(source.getCreatedTime()).setUpdatedTime(source.getUpdatedTime());
    }

    static TenantAccessResponse toResponse(TenantAccessVO source) {
        return new TenantAccessResponse().setTenantId(source.getTenantId()).setDepartmentId(source.getDepartmentId())
                .setGranted(source.isGranted());
    }

    static List<DepartmentResponse> toDepartmentResponses(List<DepartmentVO> source) {
        return source.stream().map(OrganizationResponseAssembler::toResponse).toList();
    }

    static List<MemberProfileResponse> toMemberProfileResponses(List<MemberProfileVO> source) {
        return source.stream().map(OrganizationResponseAssembler::toResponse).toList();
    }

    static Pager<MemberProfileResponse> toMemberProfilePager(Pager<MemberProfileVO> source) {
        Pager<MemberProfileResponse> pager = publicPager(source);
        pager.setObjectList(toMemberProfileResponses(source.getObjectList()));
        return pager;
    }

    static Pager<DepartmentMemberProfileResponse> toDepartmentMemberProfilePager(
            Pager<DepartmentMemberProfileVO> source) {
        Pager<DepartmentMemberProfileResponse> pager = publicPager(source);
        pager.setObjectList(source.getObjectList().stream().map(OrganizationResponseAssembler::toResponse).toList());
        return pager;
    }

    static List<MembershipRequestResponse> toMembershipRequestResponses(List<MembershipRequestVO> source) {
        return source.stream().map(OrganizationResponseAssembler::toResponse).toList();
    }

    static Pager<MembershipRequestResponse> toMembershipRequestPager(Pager<MembershipRequestVO> source) {
        Pager<MembershipRequestResponse> pager = publicPager(source);
        pager.setObjectList(toMembershipRequestResponses(source.getObjectList()));
        return pager;
    }

    static List<TenantAccessResponse> toTenantAccessResponses(List<TenantAccessVO> source) {
        return source.stream().map(OrganizationResponseAssembler::toResponse).toList();
    }

    private static <T> Pager<T> publicPager(Pager<?> source) {
        if (source == null) return null;
        if (source.getTotalNum() < 0) {
            throw new IllegalStateException("Unknown total must not reach the public page contract");
        }
        Pager<T> pager = PageUtil.copy(source);
        pager.setMeta(null);
        return pager;
    }
}
