package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Complete replacement of an opportunity's basic fields, not a partial update.
 *
 * <p>Every optional field below is replaced by exactly what this request carries, so an omitted
 * field clears the stored value and an omitted amount becomes zero. Build the request from a
 * current {@code GET /api/v1/opportunities/{opportunityId}/editable-baseline} response, which is
 * returned only when the caller can read, unmask and write every replaced field.</p>
 */
@Data
@Accessors(chain = true)
public class OpportunityUpdateRequest {
    @NotNull
    @PositiveOrZero
    private Long version;
    @NotBlank
    @Size(max = 200)
    private String name;
    @NotNull
    @Positive
    private Long customerId;
    @NotBlank
    @Size(max = 64)
    private String pipelineCode;
    @NotBlank
    @Size(max = 64)
    private String stageCode;
    @Schema(nullable = true, description = "Replaced outright; omitting it clears the stored value")
    private LocalDate expectedCloseDate;
    @DecimalMin("0")
    @Digits(integer = 16, fraction = 2)
    @Schema(nullable = true, description = "Replaced outright; omitting it stores zero")
    private BigDecimal estimatedAmount;
    @Schema(nullable = true, description = "Replaced outright; omitting it clears the stored value")
    private LocalDateTime nextContactTime;
    @Size(max = 2000)
    @Schema(nullable = true, description = "Replaced outright; omitting it clears the stored value")
    private String description;
    @Size(max = 200)
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 20) List<@Size(max = 500) String>> customFields;
    private Map<@NotBlank @Size(max = 64) String, @Size(max = 10) List<@Positive Long>> fileFields;
}
