package io.github.archetom.crm.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MarketingActivityResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"CUSTOMER", "CONTACT", "LEAD"})
    private String relatedObjectType;
    @Schema(minimum = "1")
    private Long marketingFormId;
    private String marketingFormPublicId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long createdByActorId;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(minimum = "1"))
    private List<Long> participantActorIds;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(minimum = "1"))
    private List<Long> participantDepartmentIds;
    private String activityType;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime startTime;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime endTime;
    private BigDecimal budget;
    private String address;
    private String description;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long viewCount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long submissionCount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean oneSubmissionPerUser;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"ACTIVE", "DELETED"})
    private String status;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long version;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime createdTime;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime updatedTime;
}
