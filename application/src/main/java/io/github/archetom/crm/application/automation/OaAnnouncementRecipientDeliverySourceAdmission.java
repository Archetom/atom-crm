package io.github.archetom.crm.application.automation;

import io.github.archetom.crm.application.port.out.notification.NotificationChannelAdapter;
import io.github.archetom.crm.application.port.out.notification.NotificationDelivery;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryAdmission;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliverySourceAdmission;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliverySourceKind;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryTemplate;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.domain.entity.OaAnnouncement;
import io.github.archetom.crm.domain.model.OaAnnouncementStatus;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.OaAnnouncementRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/** OA root -> immutable recipient -> delivery admission; it never maps to the Work notification inbox. */
@Component
class OaAnnouncementRecipientDeliverySourceAdmission implements NotificationDeliverySourceAdmission {
    private final NotificationDeliveryAdmissionSupport support;
    private final OaAnnouncementRepository announcementRepository;
    private final TenantAuthorizationEvaluator authorizationEvaluator;

    OaAnnouncementRecipientDeliverySourceAdmission(NotificationDeliveryAdmissionSupport support,
                                                   OaAnnouncementRepository announcementRepository,
                                                   TenantAuthorizationEvaluator authorizationEvaluator) {
        this.support = support;
        this.announcementRepository = announcementRepository;
        this.authorizationEvaluator = authorizationEvaluator;
    }

    @Override public NotificationDeliverySourceKind sourceKind() {
        return NotificationDeliverySourceKind.OA_ANNOUNCEMENT_RECIPIENT;
    }

    @Override
    public NotificationDeliveryAdmission admit(NotificationDelivery claim, NotificationChannelAdapter adapter,
                                                UUID leaseOwner, LocalDateTime now) {
        Optional<OaAnnouncement> announcement = announcementRepository.findByIdForUpdate(
                claim.tenantId(), claim.announcementId());
        if (announcement.isEmpty()) return NotificationDeliveryAdmission.none(claim);
        if (!announcementRepository.hasRecipientForDeliveryAdmission(claim.tenantId(), claim.announcementId(),
                claim.recipientMemberId())) {
            return NotificationDeliveryAdmission.none(claim);
        }
        Optional<NotificationDelivery> locked = support.lockClaim(claim, leaseOwner, now);
        if (locked.isEmpty()) return NotificationDeliveryAdmission.none(claim);
        NotificationDelivery delivery = locked.get();
        OaAnnouncement root = announcement.get();
        if (root.getStatus() != OaAnnouncementStatus.PUBLISHED || root.getPublishedTime() == null) {
            return support.suppress(delivery, leaseOwner, "ANNOUNCEMENT_WITHDRAWN", now);
        }
        if (!support.oaAnnouncementEmailEnabled(delivery)) {
            return support.suppress(delivery, leaseOwner, "PREFERENCE_OFF", now);
        }
        if (!authorized(delivery)) return support.suppress(delivery, leaseOwner, "ADMISSION_REVOKED", now);
        return support.begin(delivery, adapter, leaseOwner, now, new NotificationDeliveryDispatchContent(
                sourceKind(), root.getId().getValue(), NotificationDeliveryTemplate.OA_ANNOUNCEMENT_EMAIL_TEMPLATE_REVISION,
                NotificationDeliveryTemplate.oaAnnouncementPayloadDigest(root), "b28b:oa:" + delivery.id() + ":"
                + delivery.payloadDigest(), "B28B_OA_ANNOUNCEMENT_EMAIL_ENDPOINT_BINDING_V1",
                "OA_ANNOUNCEMENT", root.getTitle(), "NORMAL", root.getPublishedTime()));
    }

    private boolean authorized(NotificationDelivery delivery) {
        TenantAuthorizationEvaluator.Evaluation value = authorizationEvaluator.evaluate(
                delivery.tenantId(), delivery.recipientMemberId());
        return value.activeMember() && has(value, AuthorityCatalog.NOTIFICATIONS_READ)
                && has(value, AuthorityCatalog.OA_ANNOUNCEMENTS_READ);
    }

    private static boolean has(TenantAuthorizationEvaluator.Evaluation evaluation, String authority) {
        return evaluation.effectiveGrants().stream()
                .anyMatch(grant -> authority.equals(grant.authorityCode().getValue()));
    }
}
