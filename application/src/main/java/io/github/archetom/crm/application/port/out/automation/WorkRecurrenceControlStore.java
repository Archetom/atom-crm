package io.github.archetom.crm.application.port.out.automation;

import io.github.archetom.crm.application.automation.WorkRecurrencePolicy;
import io.github.archetom.crm.application.automation.WorkRecurrenceReadiness;

/** Durable mixed-replica gate; it carries no tenant, series, or rule data. */
public interface WorkRecurrenceControlStore {
    WorkRecurrenceReadiness reconcile(WorkRecurrencePolicy policy);

    WorkRecurrenceReadiness readiness(WorkRecurrencePolicy policy);

    /** Locks the fixed control row and rejects a stale effect transaction. */
    void requireCurrent(WorkRecurrencePolicy policy);
}
