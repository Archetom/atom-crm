package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** Minimal link to a currently visible target record; no field values are exposed. */
@Data
@Accessors(chain = true)
public class MarketingFormSubmissionLinkResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long submissionId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"CUSTOMER", "LEAD"})
    private String targetObjectType;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long targetRecordId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long formVersion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime submittedTime;
}
