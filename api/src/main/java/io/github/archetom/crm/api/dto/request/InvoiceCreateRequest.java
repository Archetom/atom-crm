package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class InvoiceCreateRequest {
    @NotNull @Positive
    private Long contractId;
    @Positive
    private Long ownerActorId;
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 20, fraction = 2)
    private BigDecimal amount;
    private LocalDate requestedDate;
    @NotBlank @Size(max = 50)
    private String invoiceType;
    @NotBlank @Size(max = 20)
    private String titleType;
    @NotBlank @Size(max = 200)
    private String title;
    @Size(max = 100)
    private String taxNumber;
    @Size(max = 200)
    private String bankName;
    @Size(max = 100)
    private String bankAccount;
    @Size(max = 500)
    private String registeredAddress;
    @Size(max = 50)
    private String registeredPhone;
    @Size(max = 100)
    private String contactName;
    @Size(max = 50)
    private String contactPhone;
    @Size(max = 500)
    private String shippingAddress;
    @Size(max = 2000)
    private String remark;
    @Size(max = 200)
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 20) List<@Size(max = 500) String>> customFields;
    private Long fileFieldDraftId;
    private Map<@NotBlank @Size(max = 64) String, @Size(max = 10) List<@Positive Long>> fileFields;
}
