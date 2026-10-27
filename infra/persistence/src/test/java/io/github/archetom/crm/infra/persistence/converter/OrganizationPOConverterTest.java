package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.entity.MemberProfile;
import io.github.archetom.crm.domain.entity.MembershipRequest;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.model.DepartmentStatus;
import io.github.archetom.crm.domain.model.MemberProfileStatus;
import io.github.archetom.crm.domain.model.MembershipRequestStatus;
import io.github.archetom.crm.domain.model.MembershipStatus;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.Email;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.MembershipId;
import io.github.archetom.crm.domain.valueobject.MembershipRequestId;
import io.github.archetom.crm.domain.valueobject.PhoneNumber;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.DepartmentPO;
import io.github.archetom.crm.infra.persistence.mysql.po.MemberProfilePO;
import io.github.archetom.crm.infra.persistence.mysql.po.MembershipRequestPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OrganizationMembershipPO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrganizationPOConverterTest {

    private final DepartmentPOConverter departmentConverter = new DepartmentPOConverter();
    private final MemberProfilePOConverter profileConverter = new MemberProfilePOConverter();
    private final OrganizationMembershipPOConverter membershipConverter = new OrganizationMembershipPOConverter();
    private final MembershipRequestPOConverter requestConverter = new MembershipRequestPOConverter();

    @Test
    void mapsRootDepartmentSentinelToAnAbsentDomainParent() {
        LocalDateTime created = LocalDateTime.now().minusDays(1);
        Department department = Department.reconstitute(new Department.Snapshot(
                new DepartmentId(4L), new TenantId(2L), null, "Sales", DepartmentStatus.ACTIVE,
                7L, created, LocalDateTime.now()));

        DepartmentPO po = departmentConverter.toPO(department);
        Department restored = departmentConverter.toDomain(po);

        assertAll(
                () -> assertEquals(0L, po.getParentDepartmentId()),
                () -> assertEquals(2L, po.getTenantId()),
                () -> assertNull(restored.getParentId()),
                () -> assertEquals(7L, restored.getVersion()),
                () -> assertFalse(restored.hasDomainEvents())
        );
    }

    @Test
    void mapsProfileMembershipAndRequestWithoutChangingIdentityOrTargetTenant() {
        LocalDateTime created = LocalDateTime.now().minusDays(1);
        LocalDateTime updated = LocalDateTime.now();
        MemberProfile profile = MemberProfile.reconstitute(new MemberProfile.Snapshot(
                new MemberId(7L), "Taylor", new Email("taylor@example.com"),
                new PhoneNumber("+8613800138000"), MemberProfileStatus.ACTIVE, 3L, created, updated));
        OrganizationMembership membership = OrganizationMembership.reconstitute(new OrganizationMembership.Snapshot(
                new MembershipId(8L), new TenantId(2L), new MemberId(7L), new DepartmentId(4L),
                MembershipStatus.ACTIVE, 4L, created, updated));
        MembershipRequest request = MembershipRequest.reconstitute(new MembershipRequest.Snapshot(
                new MembershipRequestId(9L), new MemberId(11L), new TenantId(2L), new DepartmentId(4L),
                "Please add me", MembershipRequestStatus.PENDING, null, 5L, created, updated));

        MemberProfilePO profilePO = profileConverter.toPO(profile);
        OrganizationMembershipPO membershipPO = membershipConverter.toPO(membership);
        MembershipRequestPO requestPO = requestConverter.toPO(request);

        MemberProfile restoredProfile = profileConverter.toDomain(profilePO);
        OrganizationMembership restoredMembership = membershipConverter.toDomain(membershipPO);
        MembershipRequest restoredRequest = requestConverter.toDomain(requestPO);

        assertAll(
                () -> assertEquals(7L, profilePO.getActorId()),
                () -> assertEquals(7L, restoredProfile.getId().getValue()),
                () -> assertEquals("taylor@example.com", restoredProfile.getEmail().getValue()),
                () -> assertEquals(2L, membershipPO.getTenantId()),
                () -> assertEquals(4L, restoredMembership.getDepartmentId().getValue()),
                () -> assertEquals(11L, requestPO.getApplicantActorId()),
                () -> assertEquals(2L, restoredRequest.getTargetTenantId().getValue()),
                () -> assertNull(restoredRequest.getReviewerId()),
                () -> assertEquals(5L, restoredRequest.getVersion()),
                () -> assertFalse(restoredRequest.hasDomainEvents())
        );
    }
}
