package io.github.archetom.crm.domain.model;

public record StageWorkflowQuery(
        BusinessObjectType objectType,
        StageWorkflowStatus status,
        String keyword,
        int page,
        int size) {

    public StageWorkflowQuery {
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (keyword != null && keyword.length() > 200 || page < 1 || size < 1 || size > 200) {
            throw new IllegalArgumentException("stage workflow query is invalid");
        }
    }
}
