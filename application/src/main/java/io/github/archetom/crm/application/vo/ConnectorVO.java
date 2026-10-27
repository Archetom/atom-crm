package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;
import java.util.List;

public record ConnectorVO(
        Long id, String connectorRef, String status, List<ConnectorCredentialVO> credentials,
        String credential, Long version, LocalDateTime createdTime,
        LocalDateTime updatedTime, LocalDateTime retiredTime) {

    public ConnectorVO {
        credentials = credentials == null ? List.of() : List.copyOf(credentials);
    }

    @Override
    public String toString() {
        return "ConnectorVO[REDACTED]";
    }
}
