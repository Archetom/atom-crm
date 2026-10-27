package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.MarketingActivityDomainException;
import io.github.archetom.crm.domain.model.MarketingActivityRelatedObjectType;
import io.github.archetom.crm.domain.model.MarketingActivityStatus;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MarketingFormId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketingActivityTest {
    private static final TenantId TENANT = new TenantId(8L);
    private static final MemberId CREATOR = new MemberId(11L);

    @Test
    void activityNormalizesParticipantsAndSoftDeleteStopsFurtherChanges() {
        MarketingActivity activity = create(List.of(new MemberId(12L), new MemberId(12L), new MemberId(13L)));

        assertEquals(List.of(new MemberId(12L), new MemberId(13L)), activity.getParticipantMemberIds());
        assertEquals(List.of(new DepartmentId(21L)), activity.getParticipantDepartmentIds());
        assertEquals(new BigDecimal("12.35"), activity.getBudget());
        activity.delete();
        assertEquals(MarketingActivityStatus.DELETED, activity.getStatus());
        assertThrows(MarketingActivityDomainException.class, () -> activity.update(
                "Spring launch", MarketingActivityRelatedObjectType.CONTACT, null,
                List.of(new MemberId(12L)),
                List.of(), null, time(1), time(2), null, null, null, 0, 0, true));
    }

    @Test
    void requiredParticipantsAndChronologicalRangeFailClosed() {
        assertThrows(MarketingActivityDomainException.class, () -> MarketingActivity.create(
                TENANT, CREATOR, "Spring launch", MarketingActivityRelatedObjectType.CUSTOMER,
                null,
                List.of(), List.of(), null, time(1), time(2), null, null, null, 0, 0, true));
        assertThrows(MarketingActivityDomainException.class, () -> MarketingActivity.create(
                TENANT, CREATOR, "Spring launch", MarketingActivityRelatedObjectType.CUSTOMER,
                null,
                List.of(new MemberId(12L)), List.of(), null, time(2), time(1), null, null, null,
                0, 0, true));
        assertThrows(MarketingActivityDomainException.class, () -> MarketingActivity.create(
                TENANT, CREATOR, "Spring launch", MarketingActivityRelatedObjectType.CUSTOMER,
                null,
                List.of(new MemberId(12L)), List.of(), null, time(1), time(2), null, null, null,
                -1, 0, true));
    }

    @Test
    void initialActivityIsActiveAndRetainsExplicitSingleSubmissionChoice() {
        MarketingActivity activity = MarketingActivity.create(
                TENANT, CREATOR, "Spring launch", MarketingActivityRelatedObjectType.LEAD,
                new MarketingFormId(31L),
                List.of(), List.of(new DepartmentId(22L)), "Webinar", time(1), time(2), null,
                null, null, 7, 3, false);

        assertTrue(activity.isActive());
        assertEquals(MarketingActivityStatus.ACTIVE, activity.getStatus());
        assertEquals(7, activity.getViewCount());
        assertEquals(3, activity.getSubmissionCount());
        assertEquals(new MarketingFormId(31L), activity.getMarketingFormId());
        assertEquals(List.of(new DepartmentId(22L)), activity.getParticipantDepartmentIds());
        assertEquals(false, activity.isOneSubmissionPerUser());
    }

    private MarketingActivity create(List<MemberId> participants) {
        return MarketingActivity.create(TENANT, CREATOR, "Spring launch",
                MarketingActivityRelatedObjectType.CUSTOMER, null,
                participants, List.of(new DepartmentId(21L)),
                "Webinar", time(1), time(2),
                new BigDecimal("12.345"), "Shanghai", "Campaign launch", 10, 2, true);
    }

    private LocalDateTime time(int day) {
        return LocalDateTime.of(2026, 8, day, 9, 0);
    }
}
