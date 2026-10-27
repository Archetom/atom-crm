package io.github.archetom.crm.domain.model;

/**
 * Closed business-record targets that an anonymous marketing form may create.
 *
 * <p>This deliberately does not mirror every {@link BusinessObjectType}: a public form must
 * have an explicit field policy, creation operation, attribution projection, and visibility
 * query before it becomes a supported target.</p>
 */
public enum MarketingFormTargetType {
    LEAD(BusinessObjectType.LEAD),
    CUSTOMER(BusinessObjectType.CUSTOMER);

    private final BusinessObjectType businessObjectType;

    MarketingFormTargetType(BusinessObjectType businessObjectType) {
        this.businessObjectType = businessObjectType;
    }

    public BusinessObjectType businessObjectType() {
        return businessObjectType;
    }
}
