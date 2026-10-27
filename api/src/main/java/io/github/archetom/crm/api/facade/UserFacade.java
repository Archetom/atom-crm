package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.UserCreateRequest;
import io.github.archetom.crm.api.dto.request.UserQueryRequest;
import io.github.archetom.crm.api.dto.response.UserPageResponse;
import io.github.archetom.crm.api.dto.response.UserResponse;
import io.github.archetom.common.result.Result;

/** Public facade contract for tenant-scoped user management. */
public interface UserFacade {

    /** Create a user inside the caller's tenant. */
    Result<UserResponse> createUser(AuthenticatedCaller caller, UserCreateRequest request);

    /** Get a visible user by tenant-scoped ID. */
    Result<UserResponse> getUserById(AuthenticatedCaller caller, Long userId);

    /** Query a bounded page of visible users. */
    Result<UserPageResponse> queryUsers(AuthenticatedCaller caller, UserQueryRequest request);

    /** Change a user's non-deleted status. */
    Result<Void> updateUserStatus(AuthenticatedCaller caller, Long userId, String status);

    /** Soft-delete a user through the deletion-specific use case. */
    Result<Void> deleteUser(AuthenticatedCaller caller, Long userId);
}
