package io.github.archetom.crm.application.vo;

import java.time.LocalDate;

public record ContractCollaboratorVO(Long actorId, String access, LocalDate effectiveUntil) {
}
