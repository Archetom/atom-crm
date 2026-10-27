package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/** Empty command: tenant, connector reference, key ID, and secret are server-derived. */
public final class ConnectorCreateRequest {

    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unknown connector create property: " + name);
    }
}
