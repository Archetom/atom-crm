package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.valueobject.MemberId;

import java.time.LocalDateTime;
import java.util.Set;

/** Fully typed fixed query; no caller-controlled sort or expression is accepted. */
public record WorkTaskQuery(
        long page,
        long size,
        MemberId viewerMemberId,
        boolean taskManager,
        WorkTaskParticipantRole participantRole,
        Set<WorkTaskStatus> statuses,
        Set<WorkTaskPriority> priorities,
        LocalDateTime dueFromInclusive,
        LocalDateTime dueBefore,
        WorkTaskBusinessRecord businessRecord
) {
    public WorkTaskQuery {
        if (page < 1 || size < 1 || size > 200 || viewerMemberId == null
                || participantRole == null) {
            throw new IllegalArgumentException("work task query is invalid");
        }
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
        priorities = priorities == null ? Set.of() : Set.copyOf(priorities);
        if (statuses.contains(WorkTaskStatus.DELETED)
                || dueFromInclusive != null && dueFromInclusive.getYear() < 1000
                || dueBefore != null && dueBefore.getYear() < 1000
                || dueFromInclusive != null && dueBefore != null
                && !dueFromInclusive.isBefore(dueBefore)) {
            throw new IllegalArgumentException("work task query filters are invalid");
        }
    }
}
