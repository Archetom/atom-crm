package io.github.archetom.crm.domain.exception;

/** Stable stage-workflow invariant violation. */
public class StageWorkflowDomainException extends DomainException {
    public StageWorkflowDomainException(String message) {
        super(message);
    }

    protected StageWorkflowDomainException(DomainError error, String message) {
        super(error, message);
    }
}
