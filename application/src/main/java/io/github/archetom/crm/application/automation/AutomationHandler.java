package io.github.archetom.crm.application.automation;

import io.github.archetom.crm.application.operation.UseCaseOperation;

/** First-party scheduled behavior registered explicitly by the composition. */
public interface AutomationHandler {
    AutomationKind kind();

    UseCaseOperation operation();

    /**
     * Gates durable materialization and claim for this fixed kind. A handler still
     * fences its effect transaction, because readiness can change after this read.
     */
    default boolean acceptsNewRuns() { return true; }

    AutomationOutcome execute(AutomationExecutionContext context);
}
