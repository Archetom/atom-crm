package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.valueobject.MemberId;

/** Fixed self-inbox query; no sort or cross-member selector exists. */
public record NotificationQuery(long page, long size, MemberId recipientMemberId, boolean unreadOnly) {
    public NotificationQuery {
        if (page < 1 || size < 1 || size > 200 || recipientMemberId == null) {
            throw new IllegalArgumentException("notification inbox query is invalid");
        }
    }
}
