package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.ActivityDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActivityModelTest {
    @Test
    void followUpIsMutableOnlyWhileActiveAndDeduplicatesRelations() {
        Activity value = Activity.followUp(new TenantId(1L), BusinessObjectType.CUSTOMER, 2L,
                new MemberId(3L), "call", null, "Discuss renewal", LocalDateTime.now(), null,
                List.of(new ActivityRelation(BusinessObjectType.CONTACT, 4L),
                        new ActivityRelation(BusinessObjectType.CONTACT, 4L)));

        assertEquals(List.of(new ActivityRelation(BusinessObjectType.CONTACT, 4L)),
                value.getRelations());
        assertDoesNotThrow(() -> value.replace("visit", "review", "Discuss renewal", LocalDateTime.now(),
                null, List.of()));
        value.delete();
        assertThrows(ActivityDomainException.class, () -> value.replace(null, null, "again",
                LocalDateTime.now(), null, List.of()));
    }

    @Test
    void unsupportedSubjectsAndOversizedRelationsFailClosed() {
        assertThrows(ActivityDomainException.class, () -> Activity.followUp(new TenantId(1L),
                BusinessObjectType.RETURN_VISIT, 2L, new MemberId(3L), null, null, "x",
                LocalDateTime.now(), null, List.of()));
        assertThrows(ActivityDomainException.class, () -> Activity.followUp(new TenantId(1L),
                BusinessObjectType.CUSTOMER, 2L, new MemberId(3L), null, null, "x",
                LocalDateTime.now(), null, java.util.stream.IntStream.range(0, 51).mapToObj(index ->
                        new ActivityRelation(BusinessObjectType.CONTACT, (long) index + 1)).toList()));
    }

    @Test
    void systemActivitiesAreAppendOnly() {
        Activity value = Activity.system(new TenantId(1L), BusinessObjectType.CUSTOMER, 2L,
                new MemberId(3L), "SYSTEM_AUDIT", "Return visit created");

        assertFalse(value.isUserFollowUp());
        assertThrows(ActivityDomainException.class, () -> value.replace(null, null, "changed",
                LocalDateTime.now(), null, List.of()));
        assertThrows(ActivityDomainException.class, value::delete);
    }
}
