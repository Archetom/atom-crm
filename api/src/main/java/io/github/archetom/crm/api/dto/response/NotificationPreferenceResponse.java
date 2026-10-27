package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** Does not expose an address, adapter, provider, readiness, or delivery ledger. */
@Data
@Accessors(chain = true)
public class NotificationPreferenceResponse {
    private Boolean emailEnabled;
    private Boolean oaAnnouncementEmailEnabled;
    private LocalDateTime updatedTime;
}
