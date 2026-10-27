package io.github.archetom.crm.domain.exception;

/**
 * user not to exception
 * @author hanfeng
 */
public class UserNotFoundException extends UserDomainException {

    public UserNotFoundException(Long userId) {
        super(DomainError.NOT_FOUND, "User does not exist");
    }

    public UserNotFoundException(String username) {
        super(DomainError.NOT_FOUND, "User does not exist");
    }
}
