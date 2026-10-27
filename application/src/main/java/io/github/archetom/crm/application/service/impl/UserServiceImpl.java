package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.api.dto.request.UserCreateRequest;
import io.github.archetom.crm.api.dto.request.UserQueryRequest;
import io.github.archetom.crm.application.assembler.UserAssembler;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.CallerGuard;
import io.github.archetom.crm.application.security.TenantAuthorizationChangeNotifier;
import io.github.archetom.crm.application.service.UserService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.transaction.AfterCommitExecutor;
import io.github.archetom.crm.application.vo.UserVO;
import io.github.archetom.crm.domain.entity.User;
import io.github.archetom.crm.domain.entity.MemberProfile;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.event.DomainEventPublisher;
import io.github.archetom.crm.domain.event.DomainEvent;
import io.github.archetom.crm.domain.exception.UserNotFoundException;
import io.github.archetom.crm.domain.factory.UserFactory;
import io.github.archetom.crm.domain.model.UserStatus;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.UserRepository;
import io.github.archetom.crm.domain.repository.MemberProfileRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.service.UserDomainService;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.UserId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * User use cases with explicit caller and tenant context.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserDomainService userDomainService;
    private final DomainEventPublisher domainEventPublisher;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;
    private final UserCacheService userCacheService;
    private final UserFactory userFactory;
    private final AfterCommitExecutor afterCommitExecutor;
    private final CallerGuard callerGuard;
    private final MemberProfileRepository memberProfileRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final SystemRoleProvisioner systemRoleProvisioner;
    private final TenantAuthorizationChangeNotifier authorizationChangeNotifier;

    @Override
    public Result<UserVO> createUser(AuthenticatedCaller caller, UserCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.USER_CREATE, new ServiceOperation<UserVO>() {
            private TenantId tenantId;
            private User user;

            @Override
            public void validate() {
                tenantId = callerGuard.requireTenant(caller, "users:write");
                if (request == null) {
                    throw new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID,
                            "Request parameters must not be empty");
                }
            }

            @Override
            public void prepare() {
                try {
                    if (request.getPhoneNumber() != null) {
                        user = userFactory.createUserWithPhone(
                                tenantId,
                                request.getUsername(),
                                request.getEmail(),
                                request.getPhoneNumber(),
                                request.getPassword(),
                                request.getRealName());
                    } else {
                        user = userFactory.createStandardUser(
                                tenantId,
                                request.getUsername(),
                                request.getEmail(),
                                request.getPassword(),
                                request.getRealName());
                    }
                } catch (IllegalArgumentException exception) {
                    throw new NonRetryableApplicationException(
                            ApplicationErrorCode.PARAMETER_INVALID,
                            "User data is invalid",
                            exception);
                }
            }

            @Override
            public UserVO execute() {
                user = userRepository.save(tenantId, user);
                createActiveMember(tenantId, user);
                return UserAssembler.INSTANCE.toVO(user);
            }

            @Override
            public void onSuccess(UserVO userVO) {
                List<DomainEvent> events = user.pullDomainEvents();
                afterCommitExecutor.execute("user domain events", () -> domainEventPublisher.publishAll(events));
                afterCommitExecutor.execute("user cache write", () -> userCacheService.cacheUser(tenantId, userVO));
                log.info("User created: userId={}, tenantId={}",
                        user.getId() != null ? user.getId().getValue() : null,
                        tenantId.getValue());
            }
        });
    }

    @Override
    public Result<UserVO> getUserById(AuthenticatedCaller caller, Long userId) {
        return queryTemplate.execute(UseCaseOperation.USER_GET, new ServiceOperation<UserVO>() {
            private TenantId tenantId;
            private UserId id;

            @Override
            public void validate() {
                tenantId = callerGuard.requireTenant(caller, "users:read");
                id = requireUserId(userId);
            }

            @Override
            public UserVO execute() {
                UserVO cachedUser = userCacheService.getCachedUser(tenantId, id);
                if (cachedUser != null) {
                    if (UserStatus.DELETED.getCode().equals(cachedUser.getStatus())) {
                        userCacheService.evictUser(tenantId, id);
                        throw new UserNotFoundException(userId);
                    }
                    return cachedUser;
                }

                User user = findVisibleUser(tenantId, id, userId);
                UserVO userVO = UserAssembler.INSTANCE.toVO(user);
                userCacheService.cacheUser(tenantId, userVO);
                return userVO;
            }
        });
    }

    @Override
    public Result<Pager<UserVO>> queryUsers(AuthenticatedCaller caller, UserQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.USER_QUERY, new ServiceOperation<Pager<UserVO>>() {
            private TenantId tenantId;
            private UserStatus status;

            @Override
            public void validate() {
                tenantId = callerGuard.requireTenant(caller, "users:read");
                if (request == null) {
                    throw new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID,
                            "Query request must not be empty");
                }
                if (request.getPage() == null) {
                    request.setPage(QueryRequest.DEFAULT_PAGE);
                } else if (request.getPage() < 1 || request.getPage() > QueryRequest.MAX_PAGE) {
                    throw new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID,
                            "Page is outside the supported range");
                }
                if (request.getSize() == null) {
                    request.setSize(QueryRequest.DEFAULT_SIZE);
                } else if (request.getSize() < 1 || request.getSize() > QueryRequest.MAX_SIZE) {
                    throw new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID,
                            "Page size is outside the supported range");
                }
                status = request.getStatus() == null
                        ? null : requireUserStatus(request.getStatus());
            }

            @Override
            public Pager<UserVO> execute() {
                PageResult<User> userPager = userRepository.findUsers(
                        tenantId,
                        request.getUsername(),
                        request.getEmail(),
                        status,
                        request.getPage(),
                        request.getSize());
                return UserAssembler.INSTANCE.toVOPager(userPager);
            }
        });
    }

    @Override
    public Result<Void> updateUserStatus(AuthenticatedCaller caller, Long userId, String status) {
        return commandTemplate.execute(UseCaseOperation.USER_STATUS_UPDATE, new ServiceOperation<Void>() {
            private TenantId tenantId;
            private UserId id;
            private User user;
            private UserStatus newStatus;

            @Override
            public void validate() {
                tenantId = callerGuard.requireTenant(caller, "users:write");
                id = requireUserId(userId);
                if (status == null) {
                    throw new NonRetryableApplicationException(
                            ApplicationErrorCode.PARAMETER_INVALID, "Status must not be empty");
                }
                newStatus = requireUserStatus(status);
                if (newStatus == UserStatus.DELETED) {
                    throw new NonRetryableApplicationException(
                            ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                            "Use the delete operation to delete a user");
                }
            }

            @Override
            public void prepare() {
                user = findVisibleUser(tenantId, id, userId);
            }

            @Override
            public Void execute() {
                user.changeStatus(newStatus, "status changed by actor " + caller.actorId());
                user = userRepository.save(tenantId, user);
                synchronizeMemberLifecycle(tenantId, user);
                authorizationChangeNotifier.changed(tenantId);
                return null;
            }

            @Override
            public void onSuccess(Void ignored) {
                List<DomainEvent> events = user.pullDomainEvents();
                afterCommitExecutor.execute("user domain events", () -> domainEventPublisher.publishAll(events));
                afterCommitExecutor.execute("user cache invalidation", () -> userCacheService.invalidateUser(tenantId, id));
                log.info("User status updated: userId={}, tenantId={}", userId, tenantId.getValue());
            }
        });
    }

    @Override
    public Result<Void> deleteUser(AuthenticatedCaller caller, Long userId) {
        return commandTemplate.execute(UseCaseOperation.USER_DELETE, new ServiceOperation<Void>() {
            private TenantId tenantId;
            private UserId id;
            private User user;

            @Override
            public void validate() {
                tenantId = callerGuard.requireTenant(caller, "users:delete");
                id = requireUserId(userId);
            }

            @Override
            public void prepare() {
                user = findVisibleUser(tenantId, id, userId);
            }

            @Override
            public Void execute() {
                if (!userDomainService.canDeleteUser(user)) {
                    throw new NonRetryableApplicationException(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                            "This user cannot be deleted");
                }
                user.delete();
                user = userRepository.save(tenantId, user);
                synchronizeMemberLifecycle(tenantId, user);
                authorizationChangeNotifier.changed(tenantId);
                return null;
            }

            @Override
            public void onSuccess(Void ignored) {
                List<DomainEvent> events = user.pullDomainEvents();
                afterCommitExecutor.execute("user domain events", () -> domainEventPublisher.publishAll(events));
                afterCommitExecutor.execute("user cache invalidation", () -> userCacheService.invalidateUser(tenantId, id));
                log.info("User deleted: userId={}, tenantId={}", userId, tenantId.getValue());
            }
        });
    }

    private UserId requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new NonRetryableApplicationException(
                    ApplicationErrorCode.PARAMETER_INVALID, "User ID must be positive");
        }
        return new UserId(userId);
    }

    /**
     * Locally created users use their persisted user ID as their verified member ID. The profile,
     * active membership, and baseline role are written in the same command transaction so the
     * account directory and the organization directory cannot diverge after a successful create.
     */
    private void createActiveMember(TenantId tenantId, User user) {
        MemberId memberId = memberId(user);
        if (memberProfileRepository.findById(memberId).isPresent()
                || membershipRepository.findByMember(tenantId, memberId).isPresent()) {
            throw new NonRetryableApplicationException(ApplicationErrorCode.RESOURCE_ALREADY_EXISTS,
                    "A verified organization member already uses this user ID");
        }
        MemberProfile profile = MemberProfile.register(memberId,
                user.getRealName() == null || user.getRealName().isBlank()
                        ? user.getUsernameValue() : user.getRealName(),
                user.getEmail(), user.getPhoneNumber());
        memberProfileRepository.save(profile);
        membershipRepository.save(tenantId, OrganizationMembership.activate(tenantId, memberId, null));
        systemRoleProvisioner.provisionMember(tenantId, memberId);
        authorizationChangeNotifier.changed(tenantId);
    }

    /** Keeps the organization entry point closed whenever its linked tenant user is not active. */
    private void synchronizeMemberLifecycle(TenantId tenantId, User user) {
        MemberId memberId = memberId(user);
        membershipRepository.findByMember(tenantId, memberId).ifPresent(membership -> {
            if (user.isActive() && !membership.isActive()) {
                membership.resume();
                membershipRepository.save(tenantId, membership);
            } else if (!user.isActive() && membership.isActive()) {
                membership.suspend();
                membershipRepository.save(tenantId, membership);
            }
        });
        memberProfileRepository.findById(memberId).ifPresent(profile -> {
            if (user.isActive() && !profile.isActive()) {
                profile.activate();
                memberProfileRepository.save(profile);
            } else if (!user.isActive() && profile.isActive()) {
                profile.deactivate();
                memberProfileRepository.save(profile);
            }
        });
    }

    private MemberId memberId(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalStateException("Persisted user ID is required for organization synchronization");
        }
        return new MemberId(user.getId().getValue());
    }

    private UserStatus requireUserStatus(String status) {
        try {
            return UserStatus.fromCode(status);
        } catch (IllegalArgumentException exception) {
            throw new NonRetryableApplicationException(
                    ApplicationErrorCode.PARAMETER_INVALID, "Unknown user status");
        }
    }

    private User findVisibleUser(TenantId tenantId, UserId id, Long rawUserId) {
        return userRepository.findById(tenantId, id)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new UserNotFoundException(rawUserId));
    }

}
