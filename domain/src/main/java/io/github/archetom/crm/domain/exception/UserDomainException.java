package io.github.archetom.crm.domain.exception;

/**
 * user domain exception
 * @author hanfeng
 */
public class UserDomainException extends DomainException {

    public UserDomainException(String message) {
        super(message);
    }

    public UserDomainException(String message, Throwable cause) {
        super(message, cause);
    }

    protected UserDomainException(DomainError error, String message) {
        super(error, message);
    }

    protected UserDomainException(DomainError error, String message, Throwable cause) {
        super(error, message, cause);
    }
}
