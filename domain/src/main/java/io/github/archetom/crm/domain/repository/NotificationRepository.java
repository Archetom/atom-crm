package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.Notification;
import io.github.archetom.crm.domain.entity.WorkTask;
import io.github.archetom.crm.domain.model.NotificationQuery;
import io.github.archetom.crm.domain.model.NotificationReadResult;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.NotificationId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.LocalDateTime;
import java.util.Optional;

/** Tenant/member-scoped Work notification inbox. */
public interface NotificationRepository {

    Notification save(TenantId tenantId, Notification notification);

    int withdrawStaleForTask(TenantId tenantId, WorkTask task, LocalDateTime now);

    void purgeExpired(LocalDateTime beforeExclusive, int limit);

    PageResult<Notification> findInboxPage(TenantId tenantId, NotificationQuery query);

    Optional<NotificationReadResult> markReadIfVisible(
            TenantId tenantId, NotificationId notificationId, MemberId recipientMemberId);

    /** Internal task-first delivery admission lock; it does not expose a user-facing inbox query. */
    Optional<Notification> findByIdForDeliveryAdmission(TenantId tenantId, NotificationId notificationId);
}
