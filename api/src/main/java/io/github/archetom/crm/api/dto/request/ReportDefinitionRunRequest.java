package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

@Data
@Accessors(chain = true)
public class ReportDefinitionRunRequest {
    @NotNull
    private LocalDate from;
    @NotNull
    private LocalDate to;
}
