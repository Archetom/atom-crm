package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.util.List;

/** Typed current-member dashboard defaults; no tenant, actor, role, or authority input is accepted. */
@Data
@Accessors(chain = true)
public class DashboardPreferenceUpdateRequest {

    @NotBlank
    @Size(max = 30)
    private String timeRange;

    private LocalDate customStartDate;

    private LocalDate customEndDate;

    @NotBlank
    @Size(max = 30)
    private String dataScope;

    /** Closed server-defined module identifiers; omitted input is an empty set. */
    @Size(max = 1)
    private List<String> hiddenModules = List.of();

    @Min(0)
    private Long version;
}
