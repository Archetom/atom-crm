package io.github.archetom.crm.application.service;

import io.github.archetom.crm.application.automation.AutomationExecutionContext;

import java.time.LocalDateTime;
import java.util.function.Supplier;

/** Internal only: exact B26 context, never an authenticated caller or public request. */
public interface WorkTaskRecurrenceService {
    int materialize(AutomationExecutionContext context, int limit,
                    Supplier<LocalDateTime> committedTimeSupplier);
}
