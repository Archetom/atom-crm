package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.util.List;

/** Current caller's typed dashboard defaults. */
@Data
@Accessors(chain = true)
public class DashboardPreferenceResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
            "TODAY", "CURRENT_WEEK", "CURRENT_MONTH", "CURRENT_QUARTER", "CURRENT_YEAR", "CUSTOM"})
    private String timeRange;
    private LocalDate customStartDate;
    private LocalDate customEndDate;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
            "SELF", "DEPARTMENT", "DEPARTMENT_SUBTREE", "TENANT"})
    private String dataScope;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(allowableValues = "CUSTOMER_CONTACT_REMINDER"))
    private List<String> hiddenModules;
    @Schema(minimum = "0", nullable = true)
    private Long version;
}
