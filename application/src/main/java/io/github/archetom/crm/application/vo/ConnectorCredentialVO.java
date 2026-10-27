package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record ConnectorCredentialVO(
        String keyId, String status, LocalDateTime createdTime, LocalDateTime revokedTime) {
}
