package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.common.result.Result;

import java.time.LocalDateTime;
import java.util.function.Supplier;

/** Human diagnostic entry point and system-only pool-release coordinator. */
public interface CustomerPoolAutomationService {
    Result<Integer> run(AuthenticatedCaller caller, LocalDateTime asOf, Integer maxCustomers);

    /**
     * Runs inside the caller-owned command transaction. The supplier is invoked only
     * after the CUSTOMER object guard and returns database UTC after verifying the run fence.
     */
    int runSystem(TenantId tenantId, LocalDateTime evaluationTime, int maxCustomers,
                  Supplier<LocalDateTime> committedTimeSupplier);
}
