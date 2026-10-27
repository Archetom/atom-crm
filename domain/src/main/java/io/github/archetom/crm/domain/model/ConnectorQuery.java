package io.github.archetom.crm.domain.model;

public record ConnectorQuery(int page, int size, ConnectorStatus status) {
    public ConnectorQuery {
        if (page < 1 || size < 1 || size > 200) {
            throw new IllegalArgumentException("connector query page is invalid");
        }
    }
}
