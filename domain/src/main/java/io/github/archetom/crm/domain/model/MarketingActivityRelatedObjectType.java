package io.github.archetom.crm.domain.model;

/** Objects for which a marketing activity may collect or expose campaign information. */
public enum MarketingActivityRelatedObjectType {
    CUSTOMER("客户", true),
    CONTACT("联系人", false),
    LEAD("线索", true);

    private final String displayName;
    private final boolean supportsMarketingForm;

    MarketingActivityRelatedObjectType(String displayName, boolean supportsMarketingForm) {
        this.displayName = displayName;
        this.supportsMarketingForm = supportsMarketingForm;
    }

    public String code() {
        return name();
    }

    public String displayName() {
        return displayName;
    }

    public boolean supportsMarketingForm() {
        return supportsMarketingForm;
    }
}
