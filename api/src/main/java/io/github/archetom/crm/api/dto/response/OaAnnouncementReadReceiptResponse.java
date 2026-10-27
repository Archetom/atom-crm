package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class OaAnnouncementReadReceiptResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long announcementId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime readTime;
}
