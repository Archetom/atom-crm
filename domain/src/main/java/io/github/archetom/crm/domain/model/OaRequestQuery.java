package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaFormId;

/** Fixed participant query for OA submissions; visibility is applied before paging. */
public record OaRequestQuery(
        MemberId memberId,
        boolean tenantManager,
        OaFormId formId,
        ApprovalInstanceStatus approvalStatus,
        int page,
        int size) {

    public OaRequestQuery {
        if (memberId == null || page < 1 || size < 1 || size > 200) {
            throw new IllegalArgumentException("OA request query is invalid");
        }
    }
}
