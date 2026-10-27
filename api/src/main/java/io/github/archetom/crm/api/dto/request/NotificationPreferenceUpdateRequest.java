package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/** Current-member external channel choice; it accepts no endpoint or provider fields. */
@Data
@Accessors(chain = true)
public class NotificationPreferenceUpdateRequest {
    @NotNull
    private Boolean emailEnabled;

    /** Omitted by legacy clients: preserve the current default-OFF OA choice. */
    private Boolean oaAnnouncementEmailEnabled;

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object ignoredValue) {
        throw new IllegalArgumentException("Unknown notification preference property");
    }
}
