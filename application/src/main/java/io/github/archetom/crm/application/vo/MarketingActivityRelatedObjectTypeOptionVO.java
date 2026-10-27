package io.github.archetom.crm.application.vo;

/** One server-defined related-object capability available to a marketing-activity editor. */
public record MarketingActivityRelatedObjectTypeOptionVO(
        String code, String displayName, boolean supportsMarketingForm) {
}
