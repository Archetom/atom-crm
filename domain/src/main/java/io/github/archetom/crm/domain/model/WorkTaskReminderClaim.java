package io.github.archetom.crm.domain.model;

import java.util.Optional;

/** One bounded claim sweep may safely terminalize exhausted rows before returning a claimant. */
public record WorkTaskReminderClaim(Optional<WorkTaskReminderOccurrence> occurrence, int deadCount) {
    public WorkTaskReminderClaim {
        if (occurrence == null || deadCount < 0) {
            throw new IllegalArgumentException("work task reminder claim result is invalid");
        }
    }
}
