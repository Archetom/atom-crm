package io.github.archetom.crm.domain.service;

import io.github.archetom.crm.domain.entity.User;
import io.github.archetom.crm.domain.valueobject.Email;
import io.github.archetom.crm.domain.valueobject.PasswordHash;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.Username;

/** Domain operations that coordinate user identity and deletion policies. */
public interface UserDomainService {

    /** Return whether a username is available inside the tenant. */
    boolean isUsernameAvailable(TenantId tenantId, Username username);

    /** Return whether an email address is available inside the tenant. */
    boolean isEmailAvailable(TenantId tenantId, Email email);

    /** Validate tenant-scoped uniqueness required for user creation. */
    void validateUserCreation(TenantId tenantId, Username username, Email email);

    /** Hash plaintext through the configured security output port. */
    PasswordHash encryptPassword(String plainPassword);

    /** Return whether domain rules permit deleting the user. */
    boolean canDeleteUser(User user);

}
