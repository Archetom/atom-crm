package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
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
public class ContractCreateRequest {
    @NotBlank @Size(max = 200)
    private String name;
    @NotBlank @Size(max = 100)
    private String number;
    @NotNull @Positive
    private Long customerId;
    @Positive
    private Long opportunityId;
    @Positive
    private Long contactId;
    @Positive
    private Long ownerActorId;
    private LocalDate orderDate;
    private LocalDate startDate;
    private LocalDate endDate;
    @Size(max = 100)
    private String contractType;
    @Size(max = 200)
    private String paymentType;
    @Size(max = 2000)
    private String remark;
    @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4)
    private BigDecimal discountRate;
    @NotNull @Size(max = 100)
    private List<@Valid ContractProductLineRequest> products;
    @Size(max = 100)
    private List<@Valid ContractCollaboratorRequest> collaborators;
    @Size(max = 200)
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 20) List<@Size(max = 500) String>> customFields;
    private Long fileFieldDraftId;
    private Map<@NotBlank @Size(max = 64) String, @Size(max = 10) List<@Positive Long>> fileFields;
}
