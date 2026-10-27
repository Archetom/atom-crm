package io.github.archetom.crm.application.automation;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.application.oa.OaAnnouncementPublicationClaim;
import io.github.archetom.crm.application.oa.OaAnnouncementPublicationOccurrence;
import io.github.archetom.crm.application.oa.OaAnnouncementPublicationOccurrenceStatus;
import io.github.archetom.crm.application.port.out.oa.OaAnnouncementPublicationStore;
import io.github.archetom.crm.application.properties.OaAnnouncementPublicationProperties;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.application.service.impl.OaAnnouncementPublicationCoordinator;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.repository.OaAnnouncementRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.valueobject.OaAnnouncementId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OaAnnouncementPublicationPumpTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 23, 12, 0);
    private static final TenantId TENANT = new TenantId(1L);

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void staleLeaseDoesNotInventCancelledOutcomeOrRetry() {
        OaAnnouncementPublicationStore store = mock(OaAnnouncementPublicationStore.class);
        OaAnnouncementRepository announcements = mock(OaAnnouncementRepository.class);
        OrganizationTenantRepository tenants = mock(OrganizationTenantRepository.class);
        BackgroundWorkObservability observability = mock(BackgroundWorkObservability.class);
        when(store.currentUtc()).thenReturn(NOW);
        when(store.claimDue(any(), eq(NOW), any(), anyInt())).thenReturn(
                new OaAnnouncementPublicationClaim(Optional.of(leasedOccurrence()), false));
        when(tenants.lockForTenantChanges(TENANT)).thenReturn(true);
        when(store.findClaimedForUpdate(eq(TENANT), eq(11L), any(), eq(3L), eq(NOW)))
                .thenReturn(Optional.empty());

        CommandServiceTemplate commands = mock(CommandServiceTemplate.class);
        when(commands.execute(any(), any(ServiceOperation.class), any())).thenAnswer(invocation -> {
            Result result = new Result<>();
            result.setData(((ServiceOperation) invocation.getArgument(1)).execute());
            result.setSuccess(true);
            return result;
        });
        OaAnnouncementPublicationProperties properties = new OaAnnouncementPublicationProperties();
        properties.setEnabled(true);
        OaAnnouncementPublicationPump pump = new OaAnnouncementPublicationPump(
                store, announcements, tenants, mock(TenantAuthorizationEvaluator.class),
                mock(OaAnnouncementPublicationCoordinator.class), properties, commands, observability);

        pump.runOnce();

        verify(observability).recordOaAnnouncementPublicationOutcome(
                OaAnnouncementPublicationMetric.CLAIMED, 1);
        verify(observability, never()).recordOaAnnouncementPublicationOutcome(
                eq(OaAnnouncementPublicationMetric.CANCELLED), anyLong());
        verify(store, never()).retry(any(), anyLong(), any(), anyLong(), any(), any());
    }

    private static OaAnnouncementPublicationOccurrence leasedOccurrence() {
        return new OaAnnouncementPublicationOccurrence(11L, TENANT, new OaAnnouncementId(7L), 1,
                NOW.minusMinutes(1), OaAnnouncementPublicationOccurrenceStatus.LEASED, 1,
                NOW.minusMinutes(1), NOW.plusDays(1), UUID.randomUUID(), 3L, NOW.plusMinutes(1),
                NOW.minusMinutes(2), null, null);
    }
}
