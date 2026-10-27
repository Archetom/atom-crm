package io.github.archetom.crm.domain.exception;

public class ConnectorDomainException extends DomainException {
    public ConnectorDomainException(String message) {
        super(message);
    }

    protected ConnectorDomainException(DomainError error, String message) {
        super(error, message);
    }
}
