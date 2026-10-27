package io.github.archetom.crm.application.port.out.notification;

import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.LocalDateTime;

/** Current-member, tenant-scoped channel preference. No endpoint is part of this model. */
public record NotificationPreference(
        TenantId tenantId, MemberId memberId, boolean emailEnabled,
        boolean oaAnnouncementEmailEnabled, LocalDateTime updatedTime) {
    public NotificationPreference {
        if (tenantId == null || memberId == null) {
            throw new IllegalArgumentException("notification preference is invalid");
        }
    }

    /** Existing Work-only preference shape; OA consent is always opt-in. */
    public NotificationPreference(TenantId tenantId, MemberId memberId, boolean emailEnabled,
                                  LocalDateTime updatedTime) {
        this(tenantId, memberId, emailEnabled, false, updatedTime);
    }
}
