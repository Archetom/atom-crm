package io.github.archetom.crm.domain.event;

import java.util.List;

/**
 * domain event publish interface - in domain layer define, application layer implementation
 * @author hanfeng
 */
public interface DomainEventPublisher {

    /**
     * publish single domain event
     */
    void publish(DomainEvent domainEvent);

    /**
     * batch publish domain event
     */
    void publishAll(List<DomainEvent> domainEvents);
}
