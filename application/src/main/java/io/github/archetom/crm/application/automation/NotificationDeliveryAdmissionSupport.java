package io.github.archetom.crm.application.automation;

import io.github.archetom.crm.application.port.out.notification.NotificationChannel;
import io.github.archetom.crm.application.port.out.notification.NotificationChannelAdapter;
import io.github.archetom.crm.application.port.out.notification.NotificationDelivery;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryAdmission;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryStatus;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryStore;
import io.github.archetom.crm.application.port.out.notification.NotificationDispatchCommand;
import io.github.archetom.crm.application.port.out.notification.NotificationDispatchGuarantee;
import io.github.archetom.crm.application.port.out.notification.NotificationDispatchGuaranteeKind;
import io.github.archetom.crm.application.port.out.notification.NotificationEndpoint;
import io.github.archetom.crm.application.port.out.notification.NotificationPreferenceStore;
import io.github.archetom.crm.application.port.out.notification.NotificationReadinessSnapshot;
import io.github.archetom.crm.application.properties.NotificationDeliveryProperties;
import io.github.archetom.crm.domain.entity.MemberProfile;
import io.github.archetom.crm.domain.repository.MemberProfileRepository;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Shared B27c protocol mechanics after an explicit source has acquired its own parent locks. */
@Component
class NotificationDeliveryAdmissionSupport {
    private final NotificationDeliveryStore deliveryStore;
    private final NotificationPreferenceStore preferenceStore;
    private final MemberProfileRepository profileRepository;
    private final NotificationDeliveryProperties properties;

    NotificationDeliveryAdmissionSupport(NotificationDeliveryStore deliveryStore,
                                         NotificationPreferenceStore preferenceStore,
                                         MemberProfileRepository profileRepository,
                                         NotificationDeliveryProperties properties) {
        this.deliveryStore = deliveryStore;
        this.preferenceStore = preferenceStore;
        this.profileRepository = profileRepository;
        this.properties = properties;
    }

    Optional<NotificationDelivery> lockClaim(NotificationDelivery claim, UUID leaseOwner, LocalDateTime now) {
        return deliveryStore.findClaimedForUpdate(claim.tenantId(), claim.id(), leaseOwner, claim.leaseFence(), now);
    }

    boolean workEmailEnabled(NotificationDelivery delivery) {
        return preferenceStore.emailEnabled(delivery.tenantId(), delivery.recipientMemberId());
    }

    boolean oaAnnouncementEmailEnabled(NotificationDelivery delivery) {
        return preferenceStore.oaAnnouncementEmailEnabled(delivery.tenantId(), delivery.recipientMemberId());
    }

    NotificationDeliveryAdmission suppress(NotificationDelivery delivery, UUID leaseOwner,
                                            String safeReason, LocalDateTime now) {
        NotificationDeliveryStatus status = deliveryStore.suppressClaim(delivery.tenantId(), delivery.id(), leaseOwner,
                delivery.leaseFence(), safeReason, now);
        return NotificationDeliveryAdmission.terminal(delivery, status);
    }

    NotificationDeliveryAdmission begin(NotificationDelivery delivery, NotificationChannelAdapter adapter,
                                         UUID leaseOwner, LocalDateTime now,
                                         NotificationDeliveryDispatchContent content) {
        if (!content.templateRevision().equals(delivery.templateRevision())
                || !content.payloadDigest().equals(delivery.payloadDigest())) {
            return suppress(delivery, leaseOwner, "PAYLOAD_DRIFT", now);
        }
        Optional<MemberProfile> profile = profileRepository.findById(delivery.recipientMemberId());
        if (profile.isEmpty() || !profile.get().isActive() || profile.get().getEmail() == null) {
            return suppress(delivery, leaseOwner, "ENDPOINT_UNAVAILABLE", now);
        }
        Optional<EndpointBinding> binding = endpointBinding(delivery,
                profile.get().getEmail().getValue(), content.endpointBindingPurpose());
        if (binding.isEmpty()) return suppress(delivery, leaseOwner, "ENDPOINT_BINDING_UNAVAILABLE", now);
        if (!endpointMatches(delivery, binding.get())) return suppress(delivery, leaseOwner, "ENDPOINT_DRIFT", now);
        NotificationReadinessSnapshot readiness = adapter.readiness(content.templateRevision());
        if (readiness == null) readiness = adapter.readiness();
        if (!ready(readiness, delivery.channel(), content.templateRevision(), now)) {
            return NotificationDeliveryAdmission.deferred(delivery);
        }
        if (!compatibleGuarantee(delivery, readiness.guarantee(), now)) {
            return suppress(delivery, leaseOwner, "GUARANTEE_DRIFT", now);
        }
        LocalDateTime idempotencyUntil = idempotencyUntil(delivery, readiness.guarantee(), now);
        if (!deliveryStore.beginDispatch(delivery.tenantId(), delivery.id(), leaseOwner, delivery.leaseFence(),
                readiness.guarantee(), idempotencyUntil, binding.get().digest(), binding.get().version(), now)) {
            return NotificationDeliveryAdmission.none(delivery);
        }
        return NotificationDeliveryAdmission.admitted(new NotificationDispatchCommand(delivery.id(), content.sourceId(),
                delivery.channel(), content.templateRevision(), content.payloadDigest(), content.idempotencyKey(),
                new NotificationEndpoint(NotificationChannel.EMAIL, profile.get().getEmail().getValue()), content.kind(),
                content.title(), content.priority(), content.dueTime()), delivery);
    }

    boolean ready(NotificationReadinessSnapshot readiness, NotificationChannel channel, String template, LocalDateTime now) {
        return readiness != null && readiness.readyAt(channel, template, properties.getAdapterContract(),
                properties.getAdapterBuild(), properties.getBindingRevision(), now);
    }

    private Optional<EndpointBinding> endpointBinding(NotificationDelivery delivery, String endpoint, String purpose) {
        String version = delivery.endpointDigestVersion() == null
                ? properties.getActiveEndpointBindingKeyVersion() : delivery.endpointDigestVersion();
        String key = properties.endpointBindingKey(version);
        if (key == null) return Optional.empty();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String input = purpose + "\u0000" + delivery.tenantId().getValue() + "\u0000" + endpoint;
            return Optional.of(new EndpointBinding(HexFormat.of().formatHex(
                    mac.doFinal(input.getBytes(StandardCharsets.UTF_8))), version));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("notification endpoint binding HMAC is unavailable", exception);
        }
    }

    private static boolean compatibleGuarantee(NotificationDelivery delivery,
                                               NotificationDispatchGuarantee readinessGuarantee,
                                               LocalDateTime now) {
        if (delivery.guaranteeKind() == null) return true;
        if (delivery.guaranteeKind() != readinessGuarantee.kind()) return false;
        return delivery.guaranteeKind() != NotificationDispatchGuaranteeKind.IDEMPOTENT_SEND
                || (delivery.idempotencyUntil() != null && delivery.idempotencyUntil().isAfter(now));
    }

    private static boolean endpointMatches(NotificationDelivery delivery, EndpointBinding binding) {
        return delivery.guaranteeKind() == null || (delivery.endpointDigest() != null
                && delivery.endpointDigest().equals(binding.digest())
                && delivery.endpointDigestVersion() != null
                && delivery.endpointDigestVersion().equals(binding.version()));
    }

    private static LocalDateTime idempotencyUntil(NotificationDelivery delivery,
                                                   NotificationDispatchGuarantee guarantee, LocalDateTime now) {
        if (delivery.guaranteeKind() == NotificationDispatchGuaranteeKind.IDEMPOTENT_SEND) {
            return delivery.idempotencyUntil();
        }
        if (guarantee.kind() != NotificationDispatchGuaranteeKind.IDEMPOTENT_SEND) return null;
        LocalDateTime value = now.plus(guarantee.idempotencyWindow());
        return value.isAfter(delivery.deadlineAt()) ? delivery.deadlineAt() : value;
    }

    private record EndpointBinding(String digest, String version) { }
}
