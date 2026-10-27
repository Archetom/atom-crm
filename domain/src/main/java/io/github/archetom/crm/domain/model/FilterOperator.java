package io.github.archetom.crm.domain.model;

public enum FilterOperator {
    EQ(1, 1),
    NOT_EQ(1, 1),
    CONTAINS(1, 1),
    NOT_CONTAINS(1, 1),
    IS_EMPTY(0, 0),
    IS_NOT_EMPTY(0, 0),
    GT(1, 1),
    GTE(1, 1),
    LT(1, 1),
    LTE(1, 1),
    IN(1, 20),
    BETWEEN(2, 2);

    private final int minimumValueCount;
    private final int maximumValueCount;

    FilterOperator(int minimumValueCount, int maximumValueCount) {
        this.minimumValueCount = minimumValueCount;
        this.maximumValueCount = maximumValueCount;
    }

    public int minimumValueCount() {
        return minimumValueCount;
    }

    public int maximumValueCount() {
        return maximumValueCount;
    }

    public boolean acceptsValueCount(int valueCount) {
        return valueCount >= minimumValueCount && valueCount <= maximumValueCount;
    }
}
