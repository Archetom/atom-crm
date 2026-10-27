package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class OaAnnouncementInboxResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String body;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean read;
    private LocalDateTime readTime;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime publishedTime;
}
