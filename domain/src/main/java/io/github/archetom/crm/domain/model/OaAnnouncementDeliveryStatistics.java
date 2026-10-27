package io.github.archetom.crm.domain.model;

public record OaAnnouncementDeliveryStatistics(long recipientTotal, long readTotal) {

    public OaAnnouncementDeliveryStatistics {
        if (recipientTotal < 0 || readTotal < 0 || readTotal > recipientTotal) {
            throw new IllegalArgumentException(
                    "OA announcement delivery statistics are invalid");
        }
    }
}
