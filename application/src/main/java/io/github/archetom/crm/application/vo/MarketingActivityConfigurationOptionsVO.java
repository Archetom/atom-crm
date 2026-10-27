package io.github.archetom.crm.application.vo;

import java.util.List;

/** Server-published choices for the marketing-activity creation and edit flow. */
public record MarketingActivityConfigurationOptionsVO(
        List<MarketingActivityTypeOptionVO> activityTypes,
        List<MarketingActivityRelatedObjectTypeOptionVO> relatedObjectTypes,
        String defaultRelatedObjectType) {
    public MarketingActivityConfigurationOptionsVO {
        activityTypes = activityTypes == null ? List.of() : List.copyOf(activityTypes);
        relatedObjectTypes = relatedObjectTypes == null ? List.of() : List.copyOf(relatedObjectTypes);
    }
}
