package io.github.archetom.crm.domain.model;

public record OaAnnouncementQuery(
        long page,
        long size,
        OaAnnouncementStatus status) {

    public OaAnnouncementQuery {
        if (page < 1 || size < 1 || size > 200
                || status == OaAnnouncementStatus.DELETED) {
            throw new IllegalArgumentException("OA announcement query is invalid");
        }
    }
}
