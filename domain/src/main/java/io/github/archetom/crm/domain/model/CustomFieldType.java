package io.github.archetom.crm.domain.model;

/** Generic storage and input types; domain-specific relations remain in their owning modules. */
public enum CustomFieldType {
    TEXT,
    TEXTAREA,
    SELECT,
    MULTI_SELECT,
    DATE,
    DATETIME,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    EMAIL,
    PHONE,
    URL,
    FILE,
    USER,
    DEPARTMENT,
    RELATION,
    ADDRESS;

    public boolean requiresOptions() {
        return this == SELECT || this == MULTI_SELECT;
    }

    public boolean supportsUniqueValues() {
        return switch (this) {
            case TEXT, INTEGER, DECIMAL, EMAIL, PHONE, URL -> true;
            default -> false;
        };
    }

    /**
     * Values persisted as one canonical scalar can participate in a stable record-list order.
     * Multi-value, relation, file, and structured address values deliberately stay unsortable.
     */
    public boolean supportsListSorting() {
        return switch (this) {
            case TEXT, TEXTAREA, SELECT, DATE, DATETIME, INTEGER, DECIMAL, BOOLEAN, EMAIL, PHONE, URL -> true;
            default -> false;
        };
    }

    public boolean isNumericScalar() {
        return this == INTEGER || this == DECIMAL;
    }
}
