package io.github.archetom.crm.domain.model;

/** Server-owned metrics shown in the fixed dashboard sales brief. */
public enum DashboardBriefMetric {
    NEW_CUSTOMER,
    NEW_CONTACT,
    NEW_OPPORTUNITY,
    NEW_CONTRACT,
    CONTRACT_AMOUNT,
    OPPORTUNITY_AMOUNT,
    RECEIVABLE_AMOUNT,
    NEW_LEAD,
    NEW_FOLLOW_UP;

    public boolean isAmountMetric() {
        return switch (this) {
            case CONTRACT_AMOUNT, OPPORTUNITY_AMOUNT, RECEIVABLE_AMOUNT -> true;
            default -> false;
        };
    }
}
