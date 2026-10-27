package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record LeadIntakeClientVO(Long id, String keyId, String apiKey, Long ownerActorId, boolean active,
                                 Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {
}
