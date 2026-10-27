package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

@Data
@Accessors(chain = true)
public class InvoiceIssueRequest {
    @NotNull @PositiveOrZero
    private Long version;
    @NotBlank @Size(max = 100)
    private String invoiceNumber;
    @NotNull
    private LocalDate issuedDate;
    @Size(max = 100)
    private String logisticsNumber;
}
