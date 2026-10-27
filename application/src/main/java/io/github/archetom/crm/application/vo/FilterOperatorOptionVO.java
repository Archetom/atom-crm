package io.github.archetom.crm.application.vo;

/** Type-validated filtering capability exposed with a field definition. */
public record FilterOperatorOptionVO(String name, int minimumValueCount, int maximumValueCount) {
}
