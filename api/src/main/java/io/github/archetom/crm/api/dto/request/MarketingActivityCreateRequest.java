package io.github.archetom.crm.api.dto.request;

import io.github.archetom.crm.api.validation.TrimmedCodePointSize;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
public class MarketingActivityCreateRequest {
    @NotBlank
    @TrimmedCodePointSize(max = 200)
    private String name;
    @NotBlank
    @Size(max = 30)
    private String relatedObjectType;
    @Schema(minimum = "1")
    @Positive
    private Long marketingFormId;
    /** Creates and links one public form for the activity's supported related object atomically. */
    @Valid
    private MarketingFormCreateRequest marketingForm;
    @ArraySchema(schema = @Schema(minimum = "1"))
    @Size(max = 100)
    private List<@NotNull @Positive Long> participantActorIds;
    @ArraySchema(schema = @Schema(minimum = "1"))
    @Size(max = 100)
    private List<@NotNull @Positive Long> participantDepartmentIds;
    @TrimmedCodePointSize(max = 100)
    private String activityType;
    @NotNull
    private LocalDateTime startTime;
    @NotNull
    private LocalDateTime endTime;
    @DecimalMin("0")
    private BigDecimal budget;
    @TrimmedCodePointSize(max = 300)
    private String address;
    @TrimmedCodePointSize(max = 2000)
    private String description;
    @Schema(minimum = "0")
    @PositiveOrZero
    private long viewCount;
    @Schema(minimum = "0")
    @PositiveOrZero
    private long submissionCount;
    private boolean oneSubmissionPerUser = true;
}
