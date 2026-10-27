package io.github.archetom.crm.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Authoritative values that a caller may safely submit to replace opportunity details.
 * Business fields are absent when {@code editable} is false.
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpportunityEditableBaselineResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long version;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean editable;
    private String name;
    private Long customerId;
    private String pipelineCode;
    private String stageCode;
    private LocalDate expectedCloseDate;
    @Schema(minimum = "0")
    private BigDecimal estimatedAmount;
    @Schema(nullable = true, description = "Optional next contact time; null clears it on update")
    private LocalDateTime nextContactTime;
    private String description;
}
