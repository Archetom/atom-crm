package io.github.archetom.crm.domain.factory;

import io.github.archetom.crm.domain.entity.User;
import io.github.archetom.crm.domain.policy.PasswordPolicy;
import io.github.archetom.crm.domain.service.UserDomainService;
import io.github.archetom.crm.domain.valueobject.Email;
import io.github.archetom.crm.domain.valueobject.PasswordHash;
import io.github.archetom.crm.domain.valueobject.PhoneNumber;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.Username;

/**
 * Creates valid user aggregates that require domain services or password policy checks.
 */
public class UserFactory {

    private final UserDomainService userDomainService;
    private final PasswordPolicy passwordPolicy;

    public UserFactory(UserDomainService userDomainService,
                       PasswordPolicy passwordPolicy) {
        this.userDomainService = userDomainService;
        this.passwordPolicy = passwordPolicy;
    }

    /** Creates a standard user after validating identity uniqueness and password policy. */
    public User createStandardUser(TenantId tenantId, String username, String email, String password, String realName) {
        Username usernameValue = new Username(username);
        Email emailValue = new Email(email);

        userDomainService.validateUserCreation(tenantId, usernameValue, emailValue);
        passwordPolicy.validate(password);

        PasswordHash passwordHash = userDomainService.encryptPassword(password);
        return User.createWithPasswordHash(
                tenantId, usernameValue, emailValue, passwordHash, realName);
    }

    /** Creates the protected administrator account used only by the one-time fresh-install flow. */
    public User createInitialAdministrator(
            TenantId tenantId, String username, String email, String password, String realName) {
        Username usernameValue = new Username(username);
        Email emailValue = new Email(email);

        userDomainService.validateUserCreation(tenantId, usernameValue, emailValue);
        passwordPolicy.validate(password);

        PasswordHash passwordHash = userDomainService.encryptPassword(password);
        return User.createInitialAdministratorWithPasswordHash(
                tenantId, usernameValue, emailValue, passwordHash, realName);
    }

    /** Creates a standard user with a validated phone number. */
    public User createUserWithPhone(TenantId tenantId, String username, String email, String phoneNumber,
                                    String password, String realName) {
        Username usernameValue = new Username(username);
        Email emailValue = new Email(email);
        PhoneNumber phoneNumberValue = new PhoneNumber(phoneNumber);

        userDomainService.validateUserCreation(tenantId, usernameValue, emailValue);
        passwordPolicy.validate(password);

        PasswordHash passwordHash = userDomainService.encryptPassword(password);
        return User.createWithPasswordHash(
                tenantId, usernameValue, emailValue, phoneNumberValue, passwordHash, realName);
    }
}
