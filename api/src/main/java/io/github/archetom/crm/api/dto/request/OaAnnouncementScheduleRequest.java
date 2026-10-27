package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

@Data
@Accessors(chain = true)
public class OaAnnouncementScheduleRequest {
    @NotNull
    @Min(0)
    private Long version;
    /** Offset is mandatory so a public request cannot be interpreted in a server time zone. */
    @NotNull
    private OffsetDateTime scheduledPublishTime;

    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unknown OA announcement schedule property: " + name);
    }
}
