package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.OaAnnouncementDomainException;
import io.github.archetom.crm.domain.model.OaAnnouncementAudienceType;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;

/** Typed draft audience selector. Recipient membership is resolved only at publish time. */
public record OaAnnouncementAudienceSelector(
        OaAnnouncementAudienceType type,
        DepartmentId departmentId,
        MemberId memberId) {

    public OaAnnouncementAudienceSelector {
        if (type == null) {
            throw new OaAnnouncementDomainException(
                    "OA announcement audience type is required");
        }
        boolean valid = switch (type) {
            case TENANT -> departmentId == null && memberId == null;
            case DEPARTMENT_SUBTREE -> departmentId != null && memberId == null;
            case MEMBER -> departmentId == null && memberId != null;
        };
        if (!valid) {
            throw new OaAnnouncementDomainException(
                    "OA announcement audience selector is invalid");
        }
    }

    public static OaAnnouncementAudienceSelector tenant() {
        return new OaAnnouncementAudienceSelector(
                OaAnnouncementAudienceType.TENANT, null, null);
    }

    public static OaAnnouncementAudienceSelector department(DepartmentId departmentId) {
        return new OaAnnouncementAudienceSelector(
                OaAnnouncementAudienceType.DEPARTMENT_SUBTREE, departmentId, null);
    }

    public static OaAnnouncementAudienceSelector member(MemberId memberId) {
        return new OaAnnouncementAudienceSelector(
                OaAnnouncementAudienceType.MEMBER, null, memberId);
    }

    public String stableKey() {
        return switch (type) {
            case TENANT -> "TENANT";
            case DEPARTMENT_SUBTREE -> "DEPARTMENT_SUBTREE:" + departmentId.getValue();
            case MEMBER -> "MEMBER:" + memberId.getValue();
        };
    }
}
