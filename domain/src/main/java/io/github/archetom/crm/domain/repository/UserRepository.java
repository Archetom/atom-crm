package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.User;
import io.github.archetom.crm.domain.model.UserStatus;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.UserId;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

/** Tenant-scoped persistence port for the User aggregate. */
public interface UserRepository {

    User save(TenantId tenantId, User user);

    Optional<User> findById(TenantId tenantId, UserId id);

    /** Resolve one account by its exact tenant-scoped login name. */
    Optional<User> findByUsername(TenantId tenantId, String username);

    /** Return the tenant-scoped user aggregates whose IDs are in the supplied collection. */
    List<User> findByIds(TenantId tenantId, Collection<UserId> ids);

    /** Return whether a username exists inside the tenant. */
    boolean existsByUsername(TenantId tenantId, String username);

    /** Return whether an email exists inside the tenant. */
    boolean existsByEmail(TenantId tenantId, String email);

    /** Query a bounded page of tenant-visible users. */
    PageResult<User> findUsers(TenantId tenantId, String username, String email, UserStatus status, int page, int size);
}
