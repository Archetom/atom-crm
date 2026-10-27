package io.github.archetom.crm.application.event;

import io.github.archetom.crm.domain.event.DomainEvent;
import io.github.archetom.crm.domain.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * domain event publish implementation
 * @author hanfeng
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DomainEventPublisherImpl implements DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(DomainEvent domainEvent) {
        if (domainEvent == null) {
            return;
        }

        log.debug(" publish domain event: {}, ID: {}", domainEvent.getEventType(), domainEvent.getAggregateId());

        // publish domain event, Spring event process
        applicationEventPublisher.publishEvent(domainEvent);
    }

    @Override
    public void publishAll(List<DomainEvent> domainEvents) {
        if (domainEvents == null || domainEvents.isEmpty()) {
            return;
        }

        domainEvents.forEach(this::publish);
    }
}
