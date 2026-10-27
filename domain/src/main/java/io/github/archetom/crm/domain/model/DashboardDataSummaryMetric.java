package io.github.archetom.crm.domain.model;

/** Closed set of server-owned facts displayed in the dashboard data-summary area. */
public enum DashboardDataSummaryMetric {
    NEW_CUSTOMER("COUNT"),
    CONVERTED_CUSTOMER("COUNT"),
    CUSTOMER_RELEASED_TO_POOL("COUNT"),
    CUSTOMER_CLAIMED_FROM_POOL("COUNT"),
    NEW_OPPORTUNITY("COUNT"),
    WON_OPPORTUNITY("COUNT"),
    LOST_OPPORTUNITY("COUNT"),
    OPPORTUNITY_AMOUNT("AMOUNT"),
    SIGNED_CONTRACT("COUNT"),
    EXPIRING_CONTRACT("COUNT"),
    EXPIRED_CONTRACT("COUNT"),
    CONTRACT_AMOUNT("AMOUNT"),
    FOLLOWED_CUSTOMER("COUNT"),
    NEW_UNFOLLOWED_CUSTOMER("COUNT"),
    RECEIVABLE_AMOUNT("AMOUNT"),
    EXPECTED_RECEIVABLE("AMOUNT");

    private final String unit;

    DashboardDataSummaryMetric(String unit) {
        this.unit = unit;
    }

    public String unit() {
        return unit;
    }
}
