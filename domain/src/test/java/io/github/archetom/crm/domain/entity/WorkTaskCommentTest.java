package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.WorkTaskDomainException;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskCommentId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkTaskCommentTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 19, 12, 0);

    @Test
    void commentUsesCodePointBoundAndOnlyAuthorCanSoftDelete() {
        WorkTaskComment comment = WorkTaskComment.create(new TenantId(1L),
                new WorkTaskId(2L), new MemberId(3L), "\u2003plain text\u2003", NOW);
        comment.onPersisted(new WorkTaskCommentId(4L), 0L, NOW, NOW);

        assertEquals("plain text", comment.getContent());
        assertThrows(WorkTaskDomainException.class,
                () -> comment.delete(new MemberId(5L), NOW.plusMinutes(1)));
        comment.delete(new MemberId(3L), NOW.plusMinutes(1));
        assertTrue(comment.isDeleted());
        assertThrows(WorkTaskDomainException.class,
                () -> comment.delete(new MemberId(3L), NOW.plusMinutes(2)));
        assertThrows(WorkTaskDomainException.class, () -> WorkTaskComment.create(
                new TenantId(1L), new WorkTaskId(2L), new MemberId(3L),
                "😀".repeat(WorkTaskComment.MAX_CONTENT_CODE_POINTS + 1), NOW));
        assertThrows(WorkTaskDomainException.class, () -> WorkTaskComment.create(
                new TenantId(1L), new WorkTaskId(2L), new MemberId(3L),
                "\u2003\t\n", NOW));
    }
}
