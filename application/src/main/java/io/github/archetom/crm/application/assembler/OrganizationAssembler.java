package io.github.archetom.crm.application.assembler;

import io.github.archetom.crm.application.vo.DepartmentVO;
import io.github.archetom.crm.application.vo.DepartmentMemberProfileVO;
import io.github.archetom.crm.application.vo.MemberProfileVO;
import io.github.archetom.crm.application.vo.MembershipRequestVO;
import io.github.archetom.crm.application.vo.OrganizationMembershipVO;
import io.github.archetom.crm.application.vo.TenantAccessVO;
import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.entity.MemberProfile;
import io.github.archetom.crm.domain.entity.MembershipRequest;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.common.result.Pager;

import java.util.List;
import java.util.Map;

/** Converts organization domain state into application outputs without exposing tenant internals. */
public final class OrganizationAssembler {

    private OrganizationAssembler() {
    }

    public static DepartmentVO toVO(Department department) {
        return new DepartmentVO(
                department.getId() == null ? null : department.getId().getValue(),
                department.getParentId() == null ? null : department.getParentId().getValue(),
                department.getName(), department.getStatus().name(), department.isActive());
    }

    public static MemberProfileVO toVO(MemberProfile profile) {
        return new MemberProfileVO(
                profile.getId().getValue(), profile.getDisplayName(),
                profile.getEmail() == null ? null : profile.getEmail().getValue(),
                profile.getPhoneNumber() == null ? null : profile.getPhoneNumber().getMasked(),
                profile.isActive());
    }

    public static OrganizationMembershipVO toVO(OrganizationMembership membership) {
        return new OrganizationMembershipVO(
                membership.getId() == null ? null : membership.getId().getValue(),
                membership.getTenantId().getValue(), membership.getMemberId().getValue(),
                membership.getDepartmentId() == null ? null : membership.getDepartmentId().getValue(),
                membership.getStatus().name(), membership.isActive());
    }

    public static MembershipRequestVO toVO(MembershipRequest request) {
        return new MembershipRequestVO(
                request.getId() == null ? null : request.getId().getValue(),
                request.getApplicantId().getValue(), request.getTargetTenantId().getValue(),
                request.getTargetDepartmentId() == null ? null : request.getTargetDepartmentId().getValue(),
                request.getMessage(), request.getStatus().name(),
                request.getReviewerId() == null ? null : request.getReviewerId().getValue(),
                request.getCreatedTime(), request.getUpdatedTime());
    }

    public static TenantAccessVO toTenantAccessVO(OrganizationMembership membership) {
        return new TenantAccessVO(membership.getTenantId().getValue(),
                membership.getDepartmentId() == null ? null : membership.getDepartmentId().getValue(),
                membership.isActive());
    }

    public static Pager<MemberProfileVO> toMemberProfilePager(PageResult<MemberProfile> profiles) {
        Pager<MemberProfileVO> pager = pager(profiles);
        pager.setObjectList(profiles.items().stream().map(OrganizationAssembler::toVO).toList());
        return pager;
    }

    public static Pager<DepartmentMemberProfileVO> toDepartmentMemberProfilePager(
            PageResult<MemberProfile> profiles,
            Map<Long, String> departmentNamesByMember,
            Map<Long, String> loginNamesByMember,
            Map<Long, List<String>> roleNamesByMember) {
        Pager<DepartmentMemberProfileVO> pager = pager(profiles);
        pager.setObjectList(profiles.items().stream().map(profile -> {
            MemberProfileVO member = toVO(profile);
            Long memberId = member.getActorId();
            return new DepartmentMemberProfileVO(
                    memberId, member.getDisplayName(), member.getEmail(), member.getMaskedPhoneNumber(),
                    member.isActive(), departmentNamesByMember.get(memberId),
                    loginNamesByMember == null ? null : loginNamesByMember.get(memberId),
                    roleNamesByMember == null ? null : roleNamesByMember.getOrDefault(memberId, List.of()));
        }).toList());
        return pager;
    }

    public static Pager<MembershipRequestVO> toMembershipRequestPager(PageResult<MembershipRequest> requests) {
        Pager<MembershipRequestVO> pager = pager(requests);
        pager.setObjectList(requests.items().stream().map(OrganizationAssembler::toVO).toList());
        return pager;
    }

    private static <T> Pager<T> pager(PageResult<?> page) {
        Pager<T> pager = new Pager<>();
        pager.setPageNum(page.page());
        pager.setPageSize(page.size());
        pager.setTotalNum(page.total());
        return pager;
    }
}
