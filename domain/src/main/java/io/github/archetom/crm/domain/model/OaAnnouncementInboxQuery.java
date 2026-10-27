package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.valueobject.MemberId;

public record OaAnnouncementInboxQuery(
        long page,
        long size,
        MemberId memberId) {

    public OaAnnouncementInboxQuery {
        if (page < 1 || size < 1 || size > 200 || memberId == null) {
            throw new IllegalArgumentException("OA announcement inbox query is invalid");
        }
    }
}
