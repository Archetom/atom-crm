package io.github.archetom.crm.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class ReturnVisitCreateRequest {
    @NotNull @Positive
    private Long customerId;
    @Positive
    private Long contactId;
    @Positive
    private Long contractId;
    @Positive
    private Long ownerActorId;
    @NotNull
    private LocalDateTime visitTime;
    @Schema(allowableValues = {"IN_PERSON", "PHONE", "SMS", "EMAIL", "WECHAT"})
    private String visitForm;
    @Schema(allowableValues = {"VERY_SATISFIED", "SATISFIED", "NEUTRAL", "DISSATISFIED", "VERY_DISSATISFIED"})
    private String satisfaction;
    @Size(max = 2000)
    private String remark;
    @Size(max = 200)
    private Map<@Size(min = 1, max = 64) String,
            @Size(max = 20) List<@Size(max = 500) String>> customFields;
    private Long fileFieldDraftId;
    private Map<@Size(min = 1, max = 64) String, @Size(max = 10) List<@Positive Long>> fileFields;
}
