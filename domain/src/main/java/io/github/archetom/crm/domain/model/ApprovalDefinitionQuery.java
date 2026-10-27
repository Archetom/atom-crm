package io.github.archetom.crm.domain.model;

public record ApprovalDefinitionQuery(
        BusinessObjectType objectType,
        ApprovalDefinitionStatus status,
        String keyword,
        int page,
        int size) {

    public ApprovalDefinitionQuery {
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (keyword != null && keyword.length() > 200 || page < 1 || size < 1 || size > 200) {
            throw new IllegalArgumentException("approval definition query is invalid");
        }
    }
}
