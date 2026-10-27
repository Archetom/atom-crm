package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class OpportunityCreateRequest {
    @NotBlank
    @Size(max = 200)
    private String name;
    @NotNull
    @Positive
    private Long customerId;
    @Positive
    private Long ownerActorId;
    @NotBlank
    @Size(max = 64)
    private String pipelineCode;
    @NotBlank
    @Size(max = 64)
    private String stageCode;
    private LocalDate expectedCloseDate;
    @DecimalMin("0")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal estimatedAmount;
    private LocalDateTime nextContactTime;
    @Size(max = 2000)
    private String description;
    @Size(max = 100)
    private List<@NotNull @Positive Long> contactIds;
    @Positive
    private Long primaryContactId;
    @Size(max = 100)
    private List<@Valid OpportunityCollaboratorRequest> collaborators;
    @Size(max = 200)
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 20) List<@Size(max = 500) String>> customFields;
    private Long fileFieldDraftId;
    private Map<@NotBlank @Size(max = 64) String, @Size(max = 10) List<@Positive Long>> fileFields;
}
