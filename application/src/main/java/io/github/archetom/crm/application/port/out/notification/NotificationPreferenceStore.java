package io.github.archetom.crm.application.port.out.notification;

import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;

/** Persistence capability for a caller's own typed channel preference. */
public interface NotificationPreferenceStore {
    NotificationPreference findOrDefault(TenantId tenantId, MemberId memberId);
    /** A null OA choice deliberately preserves the stored OA opt-in during a legacy PUT. */
    NotificationPreference update(TenantId tenantId, MemberId memberId, boolean emailEnabled,
                                  Boolean oaAnnouncementEmailEnabled, LocalDateTime updatedTime);
    boolean emailEnabled(TenantId tenantId, MemberId memberId);
    boolean oaAnnouncementEmailEnabled(TenantId tenantId, MemberId memberId);
    Set<MemberId> oaAnnouncementEmailEnabled(TenantId tenantId, Collection<MemberId> memberIds);
}
