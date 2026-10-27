package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.entity.OaAnnouncement;
import io.github.archetom.crm.domain.entity.OaAnnouncementAudienceSelector;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.OaAnnouncementAudienceType;
import io.github.archetom.crm.domain.model.OaAnnouncementPublicationSafeReason;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.OaAnnouncementRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryStore;
import io.github.archetom.crm.application.port.out.notification.NotificationPreferenceStore;
import io.github.archetom.crm.application.properties.NotificationDeliveryProperties;
import io.github.archetom.crm.application.properties.NotificationReminderProperties;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryOutcomeCounts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared in-transaction publication path for a human command and a due occurrence. */
@Component
public class OaAnnouncementPublicationCoordinator {
    private final OaAnnouncementRepository repository;
    private final DepartmentRepository departmentRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final AttachmentFileRepository attachmentFileRepository;
    private final NotificationDeliveryStore deliveryStore;
    private final NotificationPreferenceStore preferenceStore;
    private final TenantAuthorizationEvaluator authorizationEvaluator;
    private final NotificationDeliveryProperties deliveryProperties;
    private final NotificationReminderProperties reminderProperties;

    /** B28a-compatible constructor used by existing focused tests; it intentionally derives no EMAIL outbox. */
    public OaAnnouncementPublicationCoordinator(OaAnnouncementRepository repository,
                                                DepartmentRepository departmentRepository,
                                                OrganizationMembershipRepository membershipRepository) {
        this(repository, departmentRepository, membershipRepository, null,
                null, null, null, null, null);
    }

    /** Focused B29c constructor: publication admission can be exercised without notification ports. */
    public OaAnnouncementPublicationCoordinator(OaAnnouncementRepository repository,
                                                DepartmentRepository departmentRepository,
                                                OrganizationMembershipRepository membershipRepository,
                                                AttachmentFileRepository attachmentFileRepository) {
        this(repository, departmentRepository, membershipRepository, attachmentFileRepository,
                null, null, null, null, null);
    }

    /** B28b-compatible constructor retained for focused email delivery tests. */
    public OaAnnouncementPublicationCoordinator(OaAnnouncementRepository repository,
                                                DepartmentRepository departmentRepository,
                                                OrganizationMembershipRepository membershipRepository,
                                                NotificationDeliveryStore deliveryStore,
                                                NotificationPreferenceStore preferenceStore,
                                                TenantAuthorizationEvaluator authorizationEvaluator,
                                                NotificationDeliveryProperties deliveryProperties,
                                                NotificationReminderProperties reminderProperties) {
        this(repository, departmentRepository, membershipRepository, null, deliveryStore,
                preferenceStore, authorizationEvaluator, deliveryProperties, reminderProperties);
    }

    @Autowired
    public OaAnnouncementPublicationCoordinator(OaAnnouncementRepository repository,
                                                DepartmentRepository departmentRepository,
                                                OrganizationMembershipRepository membershipRepository,
                                                AttachmentFileRepository attachmentFileRepository,
                                                NotificationDeliveryStore deliveryStore,
                                                NotificationPreferenceStore preferenceStore,
                                                TenantAuthorizationEvaluator authorizationEvaluator,
                                                NotificationDeliveryProperties deliveryProperties,
                                                NotificationReminderProperties reminderProperties) {
        this.repository = repository;
        this.departmentRepository = departmentRepository;
        this.membershipRepository = membershipRepository;
        this.attachmentFileRepository = attachmentFileRepository;
        this.deliveryStore = deliveryStore;
        this.preferenceStore = preferenceStore;
        this.authorizationEvaluator = authorizationEvaluator;
        this.deliveryProperties = deliveryProperties;
        this.reminderProperties = reminderProperties;
    }

    /**
     * The caller already owns tenant -> announcement (-> occurrence) locks. This method
     * deliberately has no caller context and never starts a nested transaction.
     */
    public PublicationResult publish(OaAnnouncement announcement, LocalDateTime publishedTime) {
        if (announcement == null || announcement.getTenantId() == null || announcement.getId() == null) {
            throw new IllegalArgumentException("OA announcement publication root is invalid");
        }
        OaAnnouncementPublicationSafeReason attachmentFailure = validateAttachmentSet(announcement);
        if (attachmentFailure != null) return PublicationResult.rejected(attachmentFailure);
        OaAnnouncementPublicationSafeReason targetFailure = validateAudienceTargets(
                announcement.getTenantId(), announcement.getAudienceSelectors());
        if (targetFailure != null) return PublicationResult.rejected(targetFailure);
        List<MemberId> recipients = repository.resolveActiveRecipientIds(
                announcement.getTenantId(), announcement.getId(), OaAnnouncement.MAX_RECIPIENTS + 1);
        if (recipients.isEmpty()) {
            return PublicationResult.rejected(OaAnnouncementPublicationSafeReason.AUDIENCE_EMPTY);
        }
        if (recipients.size() > OaAnnouncement.MAX_RECIPIENTS) {
            return PublicationResult.rejected(OaAnnouncementPublicationSafeReason.AUDIENCE_LIMIT);
        }
        announcement.publish(publishedTime);
        repository.save(announcement.getTenantId(), announcement);
        repository.saveRecipientSnapshot(announcement.getTenantId(), announcement.getId(),
                recipients, publishedTime);
        createEligibleEmailDeliveries(announcement, recipients, publishedTime);
        return PublicationResult.success();
    }

    /**
     * Called while tenant -> OA announcement is locked.  The bounded relation query then locks
     * relation/file rows in file-ID order, so no attachment can become unavailable between
     * admission and the publication commit.
     */
    public OaAnnouncementPublicationSafeReason validateAttachmentSet(OaAnnouncement announcement) {
        if (announcement == null || announcement.getTenantId() == null || announcement.getId() == null) {
            throw new IllegalArgumentException("OA announcement attachment root is invalid");
        }
        if (attachmentFileRepository == null) return null;
        List<AttachmentFile> files = attachmentFileRepository.findActiveByTargetForUpdate(
                announcement.getTenantId(), new AttachmentTarget(AttachmentTargetType.OA_ANNOUNCEMENT,
                        announcement.getId().getValue()), 101);
        if (files.size() > 100 || files.stream().anyMatch(file -> !file.isAvailable())) {
            return OaAnnouncementPublicationSafeReason.ATTACHMENT_UNAVAILABLE;
        }
        return null;
    }

    /** Called while the OA root is locked by withdraw/delete, before the root lock is released. */
    public NotificationDeliveryOutcomeCounts suppressOutstandingDeliveries(
            TenantId tenantId, io.github.archetom.crm.domain.valueobject.OaAnnouncementId announcementId,
            LocalDateTime now) {
        if (deliveryStore == null) return new NotificationDeliveryOutcomeCounts(0, 0, 0);
        return deliveryStore.suppressForOaAnnouncement(tenantId, announcementId, now);
    }

    private void createEligibleEmailDeliveries(OaAnnouncement announcement, List<MemberId> recipients,
                                               LocalDateTime publishedTime) {
        if (deliveryStore == null || !deliveryProperties.dispatchEnabled(reminderProperties.getNotificationRetention())) {
            return;
        }
        Map<MemberId, TenantAuthorizationEvaluator.Evaluation> authorizations = authorizationEvaluator.evaluateAll(
                announcement.getTenantId(), recipients);
        Set<MemberId> optedIn = preferenceStore.oaAnnouncementEmailEnabled(announcement.getTenantId(), recipients);
        List<MemberId> eligible = recipients.stream().filter(optedIn::contains)
                .filter(memberId -> permitted(authorizations.get(memberId))).toList();
        if (!eligible.isEmpty()) {
            deliveryStore.createEmailForOaAnnouncementRecipients(announcement.getTenantId(), announcement,
                    eligible, publishedTime, deliveryProperties.safeRetryDeadline());
        }
    }

    private static boolean permitted(TenantAuthorizationEvaluator.Evaluation evaluation) {
        return evaluation != null && evaluation.activeMember()
                && has(evaluation, AuthorityCatalog.NOTIFICATIONS_READ)
                && has(evaluation, AuthorityCatalog.OA_ANNOUNCEMENTS_READ);
    }

    private static boolean has(TenantAuthorizationEvaluator.Evaluation evaluation, String authority) {
        return evaluation.effectiveGrants().stream()
                .anyMatch(grant -> authority.equals(grant.authorityCode().getValue()));
    }

    public OaAnnouncementPublicationSafeReason validateAudienceTargets(
            TenantId tenantId, Collection<OaAnnouncementAudienceSelector> audience) {
        if (tenantId == null || audience == null) {
            return OaAnnouncementPublicationSafeReason.AUDIENCE_INVALID;
        }
        List<DepartmentId> departmentIds = audience.stream()
                .filter(value -> value.type() == OaAnnouncementAudienceType.DEPARTMENT_SUBTREE)
                .map(OaAnnouncementAudienceSelector::departmentId).distinct().toList();
        List<Department> departments = departmentRepository.findByIds(tenantId, departmentIds);
        Set<Long> activeDepartments = new HashSet<>(departments.stream()
                .filter(Department::isActive).map(value -> value.getId().getValue()).toList());
        if (activeDepartments.size() != departmentIds.size()) {
            return OaAnnouncementPublicationSafeReason.AUDIENCE_INVALID;
        }
        List<MemberId> memberIds = audience.stream()
                .filter(value -> value.type() == OaAnnouncementAudienceType.MEMBER)
                .map(OaAnnouncementAudienceSelector::memberId).distinct().toList();
        List<OrganizationMembership> memberships = membershipRepository.findByMembers(tenantId, memberIds);
        Set<Long> activeMembers = new HashSet<>(memberships.stream()
                .filter(OrganizationMembership::isActive)
                .map(value -> value.getMemberId().getValue()).toList());
        return activeMembers.size() == memberIds.size() ? null
                : OaAnnouncementPublicationSafeReason.AUDIENCE_INVALID;
    }

    public record PublicationResult(boolean published,
                                    OaAnnouncementPublicationSafeReason rejectionReason) {
        static PublicationResult success() {
            return new PublicationResult(true, null);
        }

        static PublicationResult rejected(OaAnnouncementPublicationSafeReason reason) {
            return new PublicationResult(false, reason);
        }
    }
}
