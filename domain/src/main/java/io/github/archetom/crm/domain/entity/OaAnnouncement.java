package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.OaAnnouncementDomainException;
import io.github.archetom.crm.domain.model.OaAnnouncementStatus;
import io.github.archetom.crm.domain.model.OaAnnouncementPublicationSafeReason;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaAnnouncementId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned tenant announcement whose audience becomes immutable after publication. */
@Getter
public class OaAnnouncement extends AggregateRoot<OaAnnouncementId> {

    public static final int MAX_TITLE_CODE_POINTS = 200;
    public static final int MAX_BODY_CODE_POINTS = 20_000;
    public static final int MAX_AUDIENCE_SELECTORS = 10_000;
    public static final int MAX_RECIPIENTS = 10_000;

    private OaAnnouncementId id;
    private TenantId tenantId;
    private MemberId createdByMemberId;
    private String title;
    private String body;
    private List<OaAnnouncementAudienceSelector> audienceSelectors = List.of();
    private OaAnnouncementStatus status;
    private LocalDateTime scheduledPublishTime;
    private MemberId scheduledByMemberId;
    private long publicationGeneration;
    private OaAnnouncementPublicationSafeReason lastPublicationSafeReason;
    private LocalDateTime publishedTime;
    private LocalDateTime withdrawnTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private OaAnnouncement() {
    }

    public record Snapshot(
            OaAnnouncementId id,
            TenantId tenantId,
            MemberId createdByMemberId,
            String title,
            String body,
            List<OaAnnouncementAudienceSelector> audienceSelectors,
            OaAnnouncementStatus status,
            LocalDateTime scheduledPublishTime,
            MemberId scheduledByMemberId,
            Long publicationGeneration,
            OaAnnouncementPublicationSafeReason lastPublicationSafeReason,
            LocalDateTime publishedTime,
            LocalDateTime withdrawnTime,
            Long version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
    }

    public static OaAnnouncement create(
            TenantId tenantId, MemberId createdByMemberId,
            String title, String body,
            Collection<OaAnnouncementAudienceSelector> audienceSelectors,
            LocalDateTime now) {
        OaAnnouncement announcement = new OaAnnouncement();
        announcement.tenantId = required(tenantId, "OA announcement tenant");
        announcement.createdByMemberId = required(
                createdByMemberId, "OA announcement creator");
        announcement.status = OaAnnouncementStatus.DRAFT;
        announcement.applyDraft(title, body, audienceSelectors);
        announcement.createdTime = requiredTime(now, "OA announcement creation time");
        announcement.updatedTime = announcement.createdTime;
        return announcement;
    }

    public static OaAnnouncement reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new OaAnnouncementDomainException(
                    "OA announcement snapshot is required");
        }
        OaAnnouncement announcement = new OaAnnouncement();
        announcement.id = snapshot.id();
        announcement.tenantId = required(snapshot.tenantId(), "OA announcement tenant");
        announcement.createdByMemberId = required(
                snapshot.createdByMemberId(), "OA announcement creator");
        announcement.title = requiredText(snapshot.title(), MAX_TITLE_CODE_POINTS,
                "OA announcement title");
        announcement.body = requiredText(snapshot.body(), MAX_BODY_CODE_POINTS,
                "OA announcement body");
        announcement.audienceSelectors = selectors(snapshot.audienceSelectors());
        announcement.status = snapshot.status();
        announcement.scheduledPublishTime = optionalTime(snapshot.scheduledPublishTime());
        announcement.scheduledByMemberId = snapshot.scheduledByMemberId();
        announcement.publicationGeneration = requiredGeneration(snapshot.publicationGeneration());
        announcement.lastPublicationSafeReason = snapshot.lastPublicationSafeReason();
        announcement.publishedTime = optionalTime(snapshot.publishedTime());
        announcement.withdrawnTime = optionalTime(snapshot.withdrawnTime());
        announcement.validateLifecycle();
        announcement.restoreVersion(snapshot.version());
        announcement.createdTime = optionalTime(snapshot.createdTime());
        announcement.updatedTime = optionalTime(snapshot.updatedTime());
        return announcement;
    }

    public void update(
            String title, String body,
            Collection<OaAnnouncementAudienceSelector> audienceSelectors,
            LocalDateTime now) {
        if (status != OaAnnouncementStatus.DRAFT) {
            throw new OaAnnouncementDomainException(
                    "only draft OA announcements can be updated");
        }
        applyDraft(title, body, audienceSelectors);
        touch(now);
    }

    public void publish(LocalDateTime now) {
        if (status != OaAnnouncementStatus.DRAFT && status != OaAnnouncementStatus.SCHEDULED) {
            throw new OaAnnouncementDomainException(
                    "only draft or scheduled OA announcements can be published");
        }
        LocalDateTime actual = requiredTime(now, "OA announcement publication time");
        status = OaAnnouncementStatus.PUBLISHED;
        scheduledPublishTime = null;
        scheduledByMemberId = null;
        lastPublicationSafeReason = null;
        publishedTime = actual;
        withdrawnTime = null;
        touch(actual);
    }

    public void withdraw(LocalDateTime now) {
        if (status != OaAnnouncementStatus.PUBLISHED) {
            throw new OaAnnouncementDomainException(
                    "only published OA announcements can be withdrawn");
        }
        LocalDateTime actual = requiredTime(now, "OA announcement withdrawal time");
        status = OaAnnouncementStatus.WITHDRAWN;
        withdrawnTime = actual;
        touch(actual);
    }

    public void delete(LocalDateTime now) {
        if (status == OaAnnouncementStatus.PUBLISHED) {
            throw new OaAnnouncementDomainException(
                    "published OA announcements must be withdrawn before deletion");
        }
        if (status == OaAnnouncementStatus.DELETED) {
            throw new OaAnnouncementDomainException(
                    "deleted OA announcements are immutable");
        }
        LocalDateTime actual = requiredTime(now, "OA announcement deletion time");
        status = OaAnnouncementStatus.DELETED;
        scheduledPublishTime = null;
        scheduledByMemberId = null;
        touch(actual);
    }

    /** Creates or replaces the one current future publication generation. */
    public void schedule(MemberId schedulingMemberId, LocalDateTime scheduledTime,
                         LocalDateTime now) {
        if (status != OaAnnouncementStatus.DRAFT && status != OaAnnouncementStatus.SCHEDULED) {
            throw new OaAnnouncementDomainException(
                    "only draft or scheduled OA announcements can be scheduled");
        }
        LocalDateTime actual = requiredTime(now, "OA announcement schedule time");
        LocalDateTime due = requiredTime(scheduledTime, "OA announcement scheduled publication time");
        if (publicationGeneration == Long.MAX_VALUE) {
            throw new OaAnnouncementDomainException("OA announcement publication generation is exhausted");
        }
        status = OaAnnouncementStatus.SCHEDULED;
        scheduledPublishTime = due;
        scheduledByMemberId = required(schedulingMemberId, "OA announcement scheduling member");
        publicationGeneration++;
        lastPublicationSafeReason = null;
        publishedTime = null;
        withdrawnTime = null;
        touch(actual);
    }

    public void cancelSchedule(LocalDateTime now) {
        if (status != OaAnnouncementStatus.SCHEDULED) {
            throw new OaAnnouncementDomainException("only scheduled OA announcements can cancel scheduling");
        }
        status = OaAnnouncementStatus.DRAFT;
        scheduledPublishTime = null;
        scheduledByMemberId = null;
        lastPublicationSafeReason = null;
        touch(now);
    }

    /** Returns a due scheduled root to editable draft state with a fixed safe reason. */
    public void failScheduledPublication(long expectedGeneration,
                                        OaAnnouncementPublicationSafeReason reason,
                                        LocalDateTime now) {
        if (status != OaAnnouncementStatus.SCHEDULED || publicationGeneration != expectedGeneration) {
            throw new OaAnnouncementDomainException("OA announcement scheduled publication is stale");
        }
        if (reason == null || reason == OaAnnouncementPublicationSafeReason.RETRY
                || reason == OaAnnouncementPublicationSafeReason.CANCELLED
                || reason == OaAnnouncementPublicationSafeReason.SUPERSEDED) {
            throw new OaAnnouncementDomainException("OA announcement publication failure reason is invalid");
        }
        status = OaAnnouncementStatus.DRAFT;
        scheduledPublishTime = null;
        scheduledByMemberId = null;
        lastPublicationSafeReason = reason;
        touch(now);
    }

    public boolean isDeleted() {
        return status == OaAnnouncementStatus.DELETED;
    }

    public void onPersisted(
            OaAnnouncementId persistedId, Long persistedVersion,
            LocalDateTime created, LocalDateTime updated) {
        if (persistedId == null || id != null && !id.sameValueAs(persistedId)) {
            throw new OaAnnouncementDomainException(
                    "persisted OA announcement ID is invalid");
        }
        id = persistedId;
        restoreVersion(persistedVersion);
        if (created != null) {
            createdTime = created;
        }
        if (updated != null) {
            updatedTime = updated;
        }
    }

    private void applyDraft(
            String title, String body,
            Collection<OaAnnouncementAudienceSelector> audienceSelectors) {
        this.title = requiredText(
                title, MAX_TITLE_CODE_POINTS, "OA announcement title");
        this.body = requiredText(
                body, MAX_BODY_CODE_POINTS, "OA announcement body");
        this.audienceSelectors = selectors(audienceSelectors);
    }

    private void validateLifecycle() {
        if (status == null) {
            throw new OaAnnouncementDomainException(
                    "OA announcement status is required");
        }
        boolean valid = switch (status) {
            case DRAFT -> scheduledPublishTime == null && scheduledByMemberId == null
                    && publishedTime == null && withdrawnTime == null;
            case SCHEDULED -> scheduledPublishTime != null && scheduledByMemberId != null
                    && publishedTime == null && withdrawnTime == null
                    && lastPublicationSafeReason == null;
            case PUBLISHED -> scheduledPublishTime == null && scheduledByMemberId == null
                    && publishedTime != null && withdrawnTime == null;
            case WITHDRAWN -> scheduledPublishTime == null && scheduledByMemberId == null
                    && publishedTime != null && withdrawnTime != null;
            case DELETED -> (publishedTime == null && withdrawnTime == null)
                    || (publishedTime != null && withdrawnTime != null);
        };
        valid = valid && (status != OaAnnouncementStatus.DELETED
                || scheduledPublishTime == null && scheduledByMemberId == null);
        if (!valid) {
            throw new OaAnnouncementDomainException(
                    "OA announcement lifecycle times are invalid");
        }
    }

    private static List<OaAnnouncementAudienceSelector> selectors(
            Collection<OaAnnouncementAudienceSelector> values) {
        if (values == null || values.isEmpty()
                || values.size() > MAX_AUDIENCE_SELECTORS) {
            throw new OaAnnouncementDomainException(
                    "OA announcement audience selectors are invalid");
        }
        Map<String, OaAnnouncementAudienceSelector> unique = new LinkedHashMap<>();
        for (OaAnnouncementAudienceSelector value : values) {
            if (value == null || unique.putIfAbsent(value.stableKey(), value) != null) {
                throw new OaAnnouncementDomainException(
                        "OA announcement audience selectors must be unique");
            }
        }
        return List.copyOf(unique.values());
    }

    private static String requiredText(String value, int maximum, String description) {
        if (value == null || value.isBlank()) {
            throw new OaAnnouncementDomainException(description + " is required");
        }
        String normalized = value.strip();
        if (normalized.codePointCount(0, normalized.length()) > maximum) {
            throw new OaAnnouncementDomainException(description + " is too long");
        }
        return normalized;
    }

    private static <T> T required(T value, String description) {
        if (value == null) {
            throw new OaAnnouncementDomainException(description + " is required");
        }
        return value;
    }

    private static LocalDateTime requiredTime(LocalDateTime value, String description) {
        if (value == null || value.getYear() < 1000 || value.getYear() > 9999) {
            throw new OaAnnouncementDomainException(description + " is invalid");
        }
        return value.withNano(value.getNano() / 1_000 * 1_000);
    }

    private static LocalDateTime optionalTime(LocalDateTime value) {
        return value == null ? null : requiredTime(value, "OA announcement time");
    }

    private static long requiredGeneration(Long value) {
        if (value == null || value < 0) {
            throw new OaAnnouncementDomainException("OA announcement publication generation is invalid");
        }
        return value;
    }

    private void touch(LocalDateTime now) {
        updatedTime = requiredTime(now, "OA announcement update time");
    }
}
