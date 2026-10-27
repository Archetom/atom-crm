package io.github.archetom.crm.application.security;

import io.github.archetom.crm.application.transaction.AfterCommitExecutor;
import io.github.archetom.crm.domain.event.DomainEventPublisher;
import io.github.archetom.crm.domain.event.TenantAuthorizationChangedEvent;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Registers cache invalidation and event publication as independent post-commit actions. */
@Component
@RequiredArgsConstructor
public class TenantAuthorizationChangeNotifier {

    private final AfterCommitExecutor afterCommitExecutor;
    private final TenantAuthorityCache cache;
    private final DomainEventPublisher eventPublisher;

    public void changed(TenantId tenantId) {
        afterCommitExecutor.execute("tenant authorization cache invalidation", () -> cache.invalidateTenant(tenantId));
        afterCommitExecutor.execute("tenant authorization change event", () -> eventPublisher.publish(new TenantAuthorizationChangedEvent(tenantId)));
    }
}
