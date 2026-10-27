package io.github.archetom.crm.api.dto.response;

import java.time.LocalDateTime;

/** Timeline-only task projection. Assignment identities never decide client actions. */
public record ApprovalTaskResponse(int stepOrder, String stepName, String status,
                                   LocalDateTime actedTime) {
}
