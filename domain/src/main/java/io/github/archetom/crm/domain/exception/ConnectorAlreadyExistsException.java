package io.github.archetom.crm.domain.exception;

public final class ConnectorAlreadyExistsException extends ConnectorDomainException {
    public ConnectorAlreadyExistsException() {
        super(DomainError.ALREADY_EXISTS, "Connector reference or credential key already exists");
    }
}
