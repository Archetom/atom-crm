package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

@Data
@Accessors(chain = true)
public class ReportTrendRequest {
    @NotBlank
    @Size(max = 40)
    private String metric;
    @NotNull
    private LocalDate from;
    @NotNull
    private LocalDate to;
    @NotBlank
    @Size(max = 10)
    private String granularity;
}
