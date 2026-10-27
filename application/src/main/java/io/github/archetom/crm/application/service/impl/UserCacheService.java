package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.port.out.CacheStore;
import io.github.archetom.crm.application.vo.UserVO;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Tenant-scoped user cache facade.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCacheService {

    private static final Duration USER_CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration REFILL_FENCE_TTL = Duration.ofSeconds(5);

    private final CacheStore cacheStore;

    public void cacheUser(TenantId tenantId, UserVO user) {
        requireTenantId(tenantId);
        if (user == null || user.getId() == null) {
            return;
        }
        if (!tenantId.getValue().equals(user.getTenantId())) {
            throw new IllegalArgumentException("Cached user tenant does not match cache tenant");
        }
        UserId userId = new UserId(user.getId());
        if (!isRefillBlocked(tenantId, userId)) {
            cacheStore.put(userKey(tenantId, userId), user, USER_CACHE_TTL);
        }
    }

    public UserVO getCachedUser(TenantId tenantId, UserId userId) {
        requireTenantId(tenantId);
        if (userId == null) {
            return null;
        }
        String key = userKey(tenantId, userId);
        if (isRefillBlocked(tenantId, userId)) {
            cacheStore.evict(key);
            return null;
        }
        UserVO user = cacheStore.get(key, UserVO.class);
        if (user != null && !tenantId.getValue().equals(user.getTenantId())) {
            cacheStore.evict(key);
            log.warn("Rejected user cache entry with mismatched tenant: userId={}", userId.getValue());
            return null;
        }
        return user;
    }

    public void evictUser(TenantId tenantId, UserId userId) {
        requireTenantId(tenantId);
        if (userId != null) {
            cacheStore.evict(userKey(tenantId, userId));
        }
    }

    /** Prevent a stale concurrent reader from repopulating immediately after a committed write. */
    public void invalidateUser(TenantId tenantId, UserId userId) {
        requireTenantId(tenantId);
        if (userId != null) {
            cacheStore.put(refillFenceKey(tenantId, userId), Boolean.TRUE, REFILL_FENCE_TTL);
            cacheStore.evict(userKey(tenantId, userId));
        }
    }

    private boolean isRefillBlocked(TenantId tenantId, UserId userId) {
        return Boolean.TRUE.equals(cacheStore.get(refillFenceKey(tenantId, userId), Boolean.class));
    }

    private String userKey(TenantId tenantId, UserId userId) {
        return "tenant:%d:user:%d".formatted(tenantId.getValue(), userId.getValue());
    }

    private String refillFenceKey(TenantId tenantId, UserId userId) {
        return "tenant:%d:user:%d:refill-fence".formatted(tenantId.getValue(), userId.getValue());
    }

    private void requireTenantId(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
    }
}
