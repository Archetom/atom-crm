package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.common.result.Result;

import java.time.LocalDateTime;
import java.util.function.Supplier;

/** Human diagnostic entry point and system-only pool-release coordinator. */
public interface ContactPoolAutomationService {
    Result<Integer> run(AuthenticatedCaller caller, LocalDateTime asOf, Integer maxContacts);

    /** Runs after the CONTACT object guard and verifies a claimed system run fence. */
    int runSystem(TenantId tenantId, LocalDateTime evaluationTime, int maxContacts,
                  Supplier<LocalDateTime> committedTimeSupplier);
}
