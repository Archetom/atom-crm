package io.github.archetom.crm.application.automation;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAdoptionProvenanceStore;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAdoptionRetentionWorkStore;
import io.github.archetom.crm.application.properties.EnterpriseDataAdoptionRetentionProperties;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.OperationLogPolicy;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/** Claims one durable retention task; each purge stays strictly within the leased tenant. */
@Component
@RequiredArgsConstructor
public class EnterpriseDataAdoptionRetentionWorker implements BackgroundWorkPump {
    private final EnterpriseDataAdoptionRetentionWorkStore workStore;
    private final EnterpriseDataAdoptionProvenanceStore provenanceStore;
    private final EnterpriseDataAdoptionRetentionProperties properties;
    private final CommandServiceTemplate commandTemplate;
    private final Clock clock;
    private final UUID owner = UUID.randomUUID();

    @Override public void runOnce() {
        if (!properties.isEnabled() || !properties.valid()) return;
        workStore.claimDue(clock.instant(), properties.getLeaseDuration(), owner).ifPresent(this::purge);
    }

    private void purge(EnterpriseDataAdoptionRetentionWorkStore.Lease lease) {
        Result<Integer> result = commandTemplate.execute(UseCaseOperation.ENTERPRISE_DATA_ADOPTION_RETENTION,
                new ServiceOperation<>() {
                    @Override public Integer execute() {
                        return provenanceStore.purgeExpired(lease.tenantId(), clock.instant(),
                                properties.getBatchLimit());
                    }
                }, OperationLogPolicy.SAFE_BACKGROUND);
        if (!result.isSuccess()) {
            throw new IllegalStateException("enterprise data adoption retention failed");
        }
        // Successful deletion cascades this task with its header.  If an earlier bounded
        // record consumed the batch first, releasing makes this exact task eligible again.
        workStore.release(lease);
    }
}
