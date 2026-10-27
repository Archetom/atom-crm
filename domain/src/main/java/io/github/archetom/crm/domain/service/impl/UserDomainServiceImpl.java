package io.github.archetom.crm.domain.service.impl;

import io.github.archetom.crm.domain.entity.User;
import io.github.archetom.crm.domain.exception.UserAlreadyExistsException;
import io.github.archetom.crm.domain.exception.UserDomainException;
import io.github.archetom.crm.domain.repository.UserRepository;
import io.github.archetom.crm.domain.service.PasswordHasher;
import io.github.archetom.crm.domain.service.UserDomainService;
import io.github.archetom.crm.domain.valueobject.Email;
import io.github.archetom.crm.domain.valueobject.PasswordHash;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.Username;

/** Domain service for tenant-scoped uniqueness, password hashing, and deletion rules. */
public class UserDomainServiceImpl implements UserDomainService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public UserDomainServiceImpl(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public boolean isUsernameAvailable(TenantId tenantId, Username username) {
        return !userRepository.existsByUsername(tenantId, username.getValue());
    }

    @Override
    public boolean isEmailAvailable(TenantId tenantId, Email email) {
        return !userRepository.existsByEmail(tenantId, email.getValue());
    }

    @Override
    public void validateUserCreation(TenantId tenantId, Username username, Email email) {
        if (!isUsernameAvailable(tenantId, username)) {
            throw new UserAlreadyExistsException(username.getValue());
        }

        if (!isEmailAvailable(tenantId, email)) {
            throw UserAlreadyExistsException.byEmail(email.getValue());
        }
    }

    @Override
    public PasswordHash encryptPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.trim().isEmpty()) {
            throw new UserDomainException("Password must not be empty");
        }

        return PasswordHash.fromTrustedHash(passwordHasher.hash(plainPassword));
    }

    @Override
    public boolean canDeleteUser(User user) {
        if (user == null) {
            return false;
        }

        if (user.isDeleted()) {
            return false;
        }

        if (user.isAdmin()) {
            return false;
        }

        return true;
    }

}
