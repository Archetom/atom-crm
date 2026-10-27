package io.github.archetom.crm.application.automation;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAdoptionProvenanceStore;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAdoptionRetentionWorkStore;
import io.github.archetom.crm.application.properties.EnterpriseDataAdoptionRetentionProperties;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnterpriseDataAdoptionRetentionWorkerTest {
    private static final Instant NOW = Instant.parse("2026-07-23T13:00:00Z");
    private static final TenantId TENANT = new TenantId(23L);

    @Test
    void claimsOneDurableLeaseThenPurgesOnlyItsCarriedTenant() {
        EnterpriseDataAdoptionRetentionWorkStore work = mock(EnterpriseDataAdoptionRetentionWorkStore.class);
        EnterpriseDataAdoptionProvenanceStore provenance = mock(EnterpriseDataAdoptionProvenanceStore.class);
        EnterpriseDataAdoptionRetentionProperties properties = enabledProperties();
        EnterpriseDataAdoptionRetentionWorkStore.Lease lease =
                new EnterpriseDataAdoptionRetentionWorkStore.Lease(11L, TENANT, 91L, 4L, UUID.randomUUID());
        when(work.claimDue(eq(NOW), eq(properties.getLeaseDuration()), any())).thenReturn(Optional.of(lease));
        when(provenance.purgeExpired(TENANT, NOW, properties.getBatchLimit())).thenReturn(1);

        worker(work, provenance, properties).runOnce();

        verify(provenance).purgeExpired(TENANT, NOW, properties.getBatchLimit());
        verify(work).release(lease);
    }

    @Test
    void disabledOrInvalidConfigurationNeverClaimsSystemWork() {
        EnterpriseDataAdoptionRetentionWorkStore work = mock(EnterpriseDataAdoptionRetentionWorkStore.class);
        EnterpriseDataAdoptionRetentionProperties properties = new EnterpriseDataAdoptionRetentionProperties();

        worker(work, mock(EnterpriseDataAdoptionProvenanceStore.class), properties).runOnce();

        verify(work, never()).claimDue(any(), any(), any());
    }

    private EnterpriseDataAdoptionRetentionWorker worker(
            EnterpriseDataAdoptionRetentionWorkStore work,
            EnterpriseDataAdoptionProvenanceStore provenance,
            EnterpriseDataAdoptionRetentionProperties properties) {
        return new EnterpriseDataAdoptionRetentionWorker(work, provenance, properties,
                new CommandServiceTemplate("test-app", new TestTransactionManager()),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private EnterpriseDataAdoptionRetentionProperties enabledProperties() {
        EnterpriseDataAdoptionRetentionProperties properties = new EnterpriseDataAdoptionRetentionProperties();
        properties.setEnabled(true);
        return properties;
    }
}
