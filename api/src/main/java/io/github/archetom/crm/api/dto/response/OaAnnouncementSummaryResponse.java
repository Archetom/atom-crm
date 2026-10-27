package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class OaAnnouncementSummaryResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long id;
    private Long createdByActorId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String body;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"DRAFT", "SCHEDULED", "PUBLISHED", "WITHDRAWN", "DELETED"})
    private String status;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long recipientTotal;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long readTotal;
    private LocalDateTime scheduledPublishTime;
    private Long scheduledByActorId;
    private String lastPublicationSafeReason;
    private LocalDateTime publishedTime;
    private LocalDateTime withdrawnTime;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long version;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime createdTime;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime updatedTime;
}
