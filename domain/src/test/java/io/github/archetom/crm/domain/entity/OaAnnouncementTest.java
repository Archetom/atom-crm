package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.OaAnnouncementDomainException;
import io.github.archetom.crm.domain.model.OaAnnouncementStatus;
import io.github.archetom.crm.domain.model.OaAnnouncementPublicationSafeReason;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OaAnnouncementTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 19, 8, 0);

    @Test
    void publicationFreezesContentAndSupportsWithdrawalAndSoftDeletion() {
        OaAnnouncement announcement = OaAnnouncement.create(
                new TenantId(1L), new MemberId(2L), "Title", "Body",
                List.of(OaAnnouncementAudienceSelector.tenant()), NOW);

        announcement.publish(NOW.plusMinutes(1));
        assertEquals(OaAnnouncementStatus.PUBLISHED, announcement.getStatus());
        assertThrows(OaAnnouncementDomainException.class, () -> announcement.update(
                "Changed", "Changed", List.of(OaAnnouncementAudienceSelector.tenant()),
                NOW.plusMinutes(2)));
        assertThrows(OaAnnouncementDomainException.class,
                () -> announcement.delete(NOW.plusMinutes(2)));

        announcement.withdraw(NOW.plusMinutes(3));
        announcement.delete(NOW.plusMinutes(4));
        assertEquals(OaAnnouncementStatus.DELETED, announcement.getStatus());
    }

    @Test
    void textLimitsCountUnicodeCodePoints() {
        String maximum = "\uD83D\uDE00".repeat(OaAnnouncement.MAX_BODY_CODE_POINTS);
        OaAnnouncement.create(new TenantId(1L), new MemberId(2L), "Title", maximum,
                List.of(OaAnnouncementAudienceSelector.tenant()), NOW);

        assertThrows(OaAnnouncementDomainException.class, () -> OaAnnouncement.create(
                new TenantId(1L), new MemberId(2L), "Title", maximum + "x",
                List.of(OaAnnouncementAudienceSelector.tenant()), NOW));
    }

    @Test
    void audienceMustBeTypedUniqueAndBounded() {
        assertThrows(OaAnnouncementDomainException.class, () -> OaAnnouncement.create(
                new TenantId(1L), new MemberId(2L), "Title", "Body",
                List.of(OaAnnouncementAudienceSelector.member(new MemberId(3L)),
                        OaAnnouncementAudienceSelector.member(new MemberId(3L))), NOW));
    }

    @Test
    void scheduleFreezesDraftAndEachReplacementUsesANewGeneration() {
        OaAnnouncement announcement = OaAnnouncement.create(
                new TenantId(1L), new MemberId(2L), "Title", "Body",
                List.of(OaAnnouncementAudienceSelector.tenant()), NOW);

        announcement.schedule(new MemberId(2L), NOW.plusHours(1), NOW);
        assertEquals(OaAnnouncementStatus.SCHEDULED, announcement.getStatus());
        assertEquals(1L, announcement.getPublicationGeneration());
        assertThrows(OaAnnouncementDomainException.class, () -> announcement.update(
                "Changed", "Body", List.of(OaAnnouncementAudienceSelector.tenant()), NOW));

        announcement.schedule(new MemberId(2L), NOW.plusHours(2), NOW.plusMinutes(1));
        assertEquals(2L, announcement.getPublicationGeneration());
        announcement.failScheduledPublication(2L,
                OaAnnouncementPublicationSafeReason.MISFIRE_EXPIRED, NOW.plusHours(3));
        assertEquals(OaAnnouncementStatus.DRAFT, announcement.getStatus());
        assertEquals(OaAnnouncementPublicationSafeReason.MISFIRE_EXPIRED,
                announcement.getLastPublicationSafeReason());
    }
}
