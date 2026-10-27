package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;
import java.util.List;

public record CustomerPoolVO(
        Long id,
        String name,
        String status,
        List<Long> administratorActorIds,
        List<Long> eligibleActorIds,
        List<Long> eligibleDepartmentIds,
        Integer dailyClaimLimit,
        Integer previousOwnerCooldownDays,
        Boolean automaticReleaseEnabled,
        List<CustomerPoolRuleVO> rules,
        Long customerCount,
        Boolean administrator,
        Long version,
        LocalDateTime createdTime,
        LocalDateTime updatedTime) {
}
