package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record ReturnVisitOptionVO(Long id, String visitNumber, LocalDateTime visitTime) {
}
