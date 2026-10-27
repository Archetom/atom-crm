package io.github.archetom.crm.domain.exception;

/** Stable invariant violation for a current-member dashboard preference. */
public class DashboardPreferenceDomainException extends DomainException {

    public DashboardPreferenceDomainException(String message) {
        super(message);
    }
}
