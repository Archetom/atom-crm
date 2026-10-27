package io.github.archetom.crm.application.port.out.notification;

import io.github.archetom.crm.domain.entity.Notification;
import io.github.archetom.crm.domain.entity.OaAnnouncement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/** Fixed, code-owned payload identity. It is not a user-editable message template. */
public final class NotificationDeliveryTemplate {
    public static final String EMAIL_TEMPLATE_REVISION = "B27C_EMAIL_V1";
    public static final String OA_ANNOUNCEMENT_EMAIL_TEMPLATE_REVISION = "B28B_OA_ANNOUNCEMENT_EMAIL_V1";
    private static final List<String> EMAIL_TEMPLATE_REVISIONS = List.of(
            EMAIL_TEMPLATE_REVISION, OA_ANNOUNCEMENT_EMAIL_TEMPLATE_REVISION);

    private NotificationDeliveryTemplate() {
    }

    public static List<String> emailTemplateRevisions() {
        return EMAIL_TEMPLATE_REVISIONS;
    }

    public static String payloadDigest(Notification notification) {
        String canonical = field(EMAIL_TEMPLATE_REVISION) + field(notification.getId().getValue())
                + field(notification.getKind().name()) + field(notification.getTaskId().getValue())
                + field(notification.getTaskNotificationProjectionVersion()) + field(notification.getTitle())
                + field(notification.getPriority().name()) + field(notification.getDueTime());
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : bytes) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** OA content deliberately excludes body, selectors, recipients, and read state. */
    public static String oaAnnouncementPayloadDigest(OaAnnouncement announcement) {
        if (announcement == null || announcement.getId() == null || announcement.getPublishedTime() == null) {
            throw new IllegalArgumentException("OA announcement delivery payload is invalid");
        }
        String canonical = field(OA_ANNOUNCEMENT_EMAIL_TEMPLATE_REVISION)
                + field(NotificationDeliverySourceKind.OA_ANNOUNCEMENT_RECIPIENT.name())
                + field(announcement.getId().getValue()) + field(announcement.getTitle())
                + field(announcement.getPublishedTime());
        return digest(canonical);
    }

    private static String digest(String canonical) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : bytes) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String field(Object value) {
        String string = String.valueOf(value);
        return string.length() + ":" + string;
    }
}
