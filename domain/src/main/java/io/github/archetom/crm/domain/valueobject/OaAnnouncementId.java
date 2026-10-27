package io.github.archetom.crm.domain.valueobject;

/** Positive persisted identity of one tenant OA announcement. */
public record OaAnnouncementId(Long value) {

    public OaAnnouncementId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("OA announcement ID must be positive");
        }
    }

    public Long getValue() {
        return value;
    }

    public boolean sameValueAs(OaAnnouncementId other) {
        return other != null && value.equals(other.value);
    }
}
