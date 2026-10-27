package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.UserCreateRequest;
import io.github.archetom.crm.api.dto.request.UserQueryRequest;
import io.github.archetom.crm.api.dto.response.UserPageResponse;
import io.github.archetom.crm.api.dto.response.UserResponse;
import io.github.archetom.crm.api.facade.UserFacade;
import io.github.archetom.crm.application.assembler.UserAssembler;
import io.github.archetom.crm.application.service.UserService;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * user facade implementation
 * @author hanfeng
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserFacadeImpl implements UserFacade {

    private final UserService userService;

    @Override
    public Result<UserResponse> createUser(AuthenticatedCaller caller, UserCreateRequest request) {
        return ResultUtil.map(userService.createUser(caller, request), UserAssembler.INSTANCE::toResponse);
    }

    @Override
    public Result<UserResponse> getUserById(AuthenticatedCaller caller, Long userId) {
        return ResultUtil.map(userService.getUserById(caller, userId), UserAssembler.INSTANCE::toResponse);
    }

    @Override
    public Result<UserPageResponse> queryUsers(AuthenticatedCaller caller, UserQueryRequest request) {
        return ResultUtil.map(userService.queryUsers(caller, request), UserAssembler.INSTANCE::toPageResponse);
    }

    @Override
    public Result<Void> updateUserStatus(AuthenticatedCaller caller, Long userId, String status) {
        return userService.updateUserStatus(caller, userId, status);
    }

    @Override
    public Result<Void> deleteUser(AuthenticatedCaller caller, Long userId) {
        return userService.deleteUser(caller, userId);
    }
}
