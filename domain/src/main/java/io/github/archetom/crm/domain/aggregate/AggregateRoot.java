package io.github.archetom.crm.domain.aggregate;

import io.github.archetom.crm.domain.event.DomainEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * aggregate root class
 * @author hanfeng
 */
public abstract class AggregateRoot<ID> {

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Long version = 0L;

    /**
     * get aggregate root ID
     */
    public abstract ID getId();

    /**
     * get domain event ()
     */
    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * Atomically copies and clears pending events for post-commit dispatch.
     */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    /**
     * clear domain event
     */
    public void clearDomainEvents() {
        domainEvents.clear();
    }

    /**
     * add domain event
     */
    protected void addDomainEvent(DomainEvent event) {
        if (event != null) {
            domainEvents.add(event);
        }
    }

    /**
     * remove domain event
     */
    protected void removeDomainEvent(DomainEvent event) {
        domainEvents.remove(event);
    }

    /**
     * check whether domain event
     */
    public boolean hasDomainEvents() {
        return !domainEvents.isEmpty();
    }

    public Long getVersion() {
        return version;
    }

    /**
     * Restores the persistence version without creating a domain event.
     */
    protected void restoreVersion(Long version) {
        this.version = version == null ? 0L : version;
    }
}
