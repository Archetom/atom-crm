package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.infra.persistence.support.ConstraintViolations;

import io.github.archetom.crm.domain.entity.User;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.UserAlreadyExistsException;
import io.github.archetom.crm.domain.model.UserStatus;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.UserRepository;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.UserId;
import io.github.archetom.crm.infra.persistence.converter.UserPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.UserMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.UserPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

/**
 * Tenant-scoped user repository implementation.
 *
 * <p>Every operation requires a tenant explicitly. A missing tenant can never
 * degrade into an unscoped query.</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private static final String USERNAME_UNIQUE_CONSTRAINT = "uk_t_user_tenant_username";
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_t_user_tenant_email";

    private final UserMapper userMapper;
    private final UserPOConverter userPOConverter;

    @Override
    public User save(TenantId tenantId, User user) {
        requireTenantMatch(tenantId, user);
        UserPO userPO = userPOConverter.toPO(user);

        try {
            if (userPO.getId() == null) {
                if (userMapper.insert(userPO) != 1) {
                    throw new IllegalStateException("User insert did not affect exactly one row");
                }
                log.info("Created user: id={}, tenantId={}", userPO.getId(), tenantId.getValue());
            } else {
                LambdaQueryWrapper<UserPO> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(UserPO::getId, userPO.getId())
                        .eq(UserPO::getTenantId, tenantId.getValue());
                if (userMapper.update(userPO, wrapper) != 1) {
                    throw new AggregateVersionConflictException();
                }
                log.info("Updated user: id={}, tenantId={}", userPO.getId(), tenantId.getValue());
            }
        } catch (DuplicateKeyException exception) {
            throw translateDuplicateKey(user, exception);
        }

        user.onPersisted(
                new UserId(userPO.getId()),
                userPO.getVersion(),
                userPO.getCreatedTime(),
                userPO.getUpdatedTime());
        return user;
    }

    @Override
    public Optional<User> findById(TenantId tenantId, UserId id) {
        requireTenantId(tenantId);
        if (id == null) {
            return Optional.empty();
        }
        LambdaQueryWrapper<UserPO> wrapper = tenantFilter(tenantId);
        wrapper.eq(UserPO::getId, id.getValue());
        return Optional.ofNullable(userPOConverter.toDomain(userMapper.selectOne(wrapper)));
    }

    @Override
    public Optional<User> findByUsername(TenantId tenantId, String username) {
        requireTenantId(tenantId);
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(userPOConverter.toDomain(userMapper.selectOne(tenantFilter(tenantId)
                .eq(UserPO::getUsername, username))));
    }

    @Override
    public List<User> findByIds(TenantId tenantId, Collection<UserId> ids) {
        requireTenantId(tenantId);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = ids.stream().filter(java.util.Objects::nonNull)
                .map(UserId::getValue).distinct().toList();
        if (userIds.isEmpty()) {
            return List.of();
        }
        return userPOConverter.toDomainList(userMapper.selectList(tenantFilter(tenantId)
                .in(UserPO::getId, userIds).orderByAsc(UserPO::getId)));
    }

    @Override
    public boolean existsByUsername(TenantId tenantId, String username) {
        LambdaQueryWrapper<UserPO> wrapper = tenantFilter(tenantId);
        wrapper.eq(UserPO::getUsername, username);
        return userMapper.selectCount(wrapper) > 0;
    }

    @Override
    public boolean existsByEmail(TenantId tenantId, String email) {
        LambdaQueryWrapper<UserPO> wrapper = tenantFilter(tenantId);
        wrapper.eq(UserPO::getEmail, email);
        return userMapper.selectCount(wrapper) > 0;
    }

    @Override
    public PageResult<User> findUsers(TenantId tenantId, String username, String email,
                                 UserStatus status, int page, int size) {
        requireTenantId(tenantId);
        Page<UserPO> poPage = new Page<>(page, size);
        String statusCode = status != null ? status.getCode() : null;
        IPage<UserPO> result = userMapper.selectUserPage(
                poPage, username, email, statusCode, tenantId.getValue());

        return new PageResult<>(
                result.getCurrent(),
                result.getSize(),
                Math.max(result.getTotal(), 0L),
                userPOConverter.toDomainList(result.getRecords()));
    }

    private LambdaQueryWrapper<UserPO> tenantFilter(TenantId tenantId) {
        requireTenantId(tenantId);
        LambdaQueryWrapper<UserPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPO::getTenantId, tenantId.getValue());
        return wrapper;
    }

    private void requireTenantMatch(TenantId tenantId, User user) {
        requireTenantId(tenantId);
        if (user == null || user.getTenantId() == null
                || !tenantId.sameValueAs(user.getTenantId())) {
            throw new IllegalArgumentException("User tenant does not match repository tenant");
        }
    }

    private void requireTenantId(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
    }

    private UserAlreadyExistsException translateDuplicateKey(User user, DuplicateKeyException exception) {
        if (ConstraintViolations.causeContains(exception, EMAIL_UNIQUE_CONSTRAINT)) {
            return UserAlreadyExistsException.byEmail(user.getEmailValue());
        }
        if (ConstraintViolations.causeContains(exception, USERNAME_UNIQUE_CONSTRAINT)) {
            return new UserAlreadyExistsException(user.getUsernameValue());
        }
        // The current schema has only username/email unique keys. Preserve a
        // stable conflict response if a driver omits the constraint name.
        return new UserAlreadyExistsException(user.getUsernameValue());
    }

}
