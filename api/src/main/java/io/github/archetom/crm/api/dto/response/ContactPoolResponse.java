package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
public class ContactPoolResponse {
    private Long id;
    private String name;
    private String status;
    private List<Long> administratorActorIds;
    private List<Long> eligibleActorIds;
    private List<Long> eligibleDepartmentIds;
    private Integer dailyClaimLimit;
    private Integer previousOwnerCooldownDays;
    private Boolean automaticReleaseEnabled;
    private List<ContactPoolRuleResponse> rules;
    private Long contactCount;
    private Boolean administrator;
    private Long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
