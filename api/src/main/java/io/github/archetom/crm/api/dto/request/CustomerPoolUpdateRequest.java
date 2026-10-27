package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class CustomerPoolUpdateRequest {
    @NotNull
    @PositiveOrZero
    private Long version;
    @NotBlank
    @Size(max = 100)
    private String name;
    @NotEmpty
    @Size(max = 50)
    private List<@Positive Long> administratorActorIds;
    @Size(max = 500)
    private List<@Positive Long> eligibleActorIds;
    @Size(max = 200)
    private List<@Positive Long> eligibleDepartmentIds;
    @Min(0)
    @Max(10000)
    private Integer dailyClaimLimit;
    @Min(0)
    @Max(3650)
    private Integer previousOwnerCooldownDays;
    private Boolean automaticReleaseEnabled;
    @Size(max = 10)
    private List<@Valid CustomerPoolRuleRequest> rules;
}
