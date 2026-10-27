package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class InvoiceProfileRequest {
    @Positive
    private Long customerId;
    @PositiveOrZero
    private Long version;
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
    @Size(max = 1000)
    private String remark;
}
