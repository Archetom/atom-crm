package io.github.archetom.crm;

import io.github.archetom.crm.application.properties.WorkRecurrenceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Separates safe scheduled-delay resolution from the reconciliation bean itself. */
@Component("workRecurrenceReconciliationDelay")
@RequiredArgsConstructor
public class WorkRecurrenceReconciliationDelay {
    private final WorkRecurrenceProperties properties;

    public long milliseconds() {
        return properties.safeReconciliationDelay().toMillis();
    }
}
