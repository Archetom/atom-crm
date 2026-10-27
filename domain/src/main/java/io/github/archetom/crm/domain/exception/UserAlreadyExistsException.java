package io.github.archetom.crm.domain.exception;

/**
 * user already exists exception
 * @author hanfeng
 */
public class UserAlreadyExistsException extends UserDomainException {

    public UserAlreadyExistsException(String username) {
        super(DomainError.ALREADY_EXISTS, "Username already exists");
    }

    public static UserAlreadyExistsException byEmail(String email) {
        return new UserAlreadyExistsException();
    }

    private UserAlreadyExistsException() {
        super(DomainError.ALREADY_EXISTS, "Email already exists");
    }
}
