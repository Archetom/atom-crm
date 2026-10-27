package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.OaAnnouncement;
import io.github.archetom.crm.domain.model.OaAnnouncementDeliveryStatistics;
import io.github.archetom.crm.domain.model.OaAnnouncementInboxItem;
import io.github.archetom.crm.domain.model.OaAnnouncementInboxQuery;
import io.github.archetom.crm.domain.model.OaAnnouncementQuery;
import io.github.archetom.crm.domain.model.OaAnnouncementSummary;
import io.github.archetom.crm.domain.model.OaAnnouncementStatus;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaAnnouncementId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface OaAnnouncementRepository {

    OaAnnouncement save(TenantId tenantId, OaAnnouncement announcement);

    Optional<OaAnnouncement> findById(TenantId tenantId, OaAnnouncementId announcementId);

    Optional<OaAnnouncement> findByIdForUpdate(
            TenantId tenantId, OaAnnouncementId announcementId);

    PageResult<OaAnnouncementSummary> findPage(
            TenantId tenantId, OaAnnouncementQuery query);

    long countByStatus(TenantId tenantId, OaAnnouncementStatus status);

    Map<OaAnnouncementId, OaAnnouncementDeliveryStatistics> findStatistics(
            TenantId tenantId, Collection<OaAnnouncementId> announcementIds);

    List<MemberId> resolveActiveRecipientIds(
            TenantId tenantId, OaAnnouncementId announcementId, int limit);

    void saveRecipientSnapshot(
            TenantId tenantId, OaAnnouncementId announcementId,
            Collection<MemberId> recipientIds, LocalDateTime deliveredTime);

    boolean hasRecipientForDeliveryAdmission(
            TenantId tenantId, OaAnnouncementId announcementId, MemberId memberId);

    /**
     * Checks the immutable recipient snapshot without taking a row lock.
     *
     * <p>This is intended for read-only recipient visibility checks. Delivery and other command
     * flows must use {@link #hasRecipientForDeliveryAdmission(TenantId, OaAnnouncementId,
     * MemberId)} so their snapshot row remains locked.</p>
     */
    boolean hasRecipientSnapshot(
            TenantId tenantId, OaAnnouncementId announcementId, MemberId memberId);

    Optional<OaAnnouncementInboxItem> findInboxItem(
            TenantId tenantId, OaAnnouncementId announcementId, MemberId memberId);

    Optional<OaAnnouncementInboxItem> findInboxItemForShare(
            TenantId tenantId, OaAnnouncementId announcementId, MemberId memberId);

    PageResult<OaAnnouncementInboxItem> findInboxPage(
            TenantId tenantId, OaAnnouncementInboxQuery query);

    long countUnread(TenantId tenantId, MemberId memberId);

    Optional<LocalDateTime> findReadTime(
            TenantId tenantId, OaAnnouncementId announcementId, MemberId memberId);

    LocalDateTime saveReadReceiptIfAbsent(
            TenantId tenantId, OaAnnouncementId announcementId,
            MemberId memberId, LocalDateTime readTime);
}
