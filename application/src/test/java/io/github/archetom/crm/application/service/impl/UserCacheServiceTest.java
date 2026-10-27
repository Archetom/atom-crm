package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.port.out.CacheStore;
import io.github.archetom.crm.application.vo.UserVO;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.UserId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserCacheServiceTest {

    private final InMemoryCache cache = new InMemoryCache();
    private final UserCacheService service = new UserCacheService(cache);

    @Test
    void isolatesIdenticalUserIdsByTenant() {
        TenantId tenantA = new TenantId(1L);
        TenantId tenantB = new TenantId(2L);
        UserId userId = new UserId(100L);

        service.cacheUser(tenantA, user(100L, 1L));

        assertNotNull(service.getCachedUser(tenantA, userId));
        assertNull(service.getCachedUser(tenantB, userId));
    }

    @Test
    void rejectsAndEvictsPayloadWithWrongTenant() {
        TenantId tenant = new TenantId(1L);
        UserId userId = new UserId(100L);
        String key = "tenant:1:user:100";
        cache.put(key, user(100L, 2L), Duration.ofMinutes(1));

        assertNull(service.getCachedUser(tenant, userId));
        assertFalse(cache.values.containsKey(key));
    }

    @Test
    void invalidationFenceRejectsStaleConcurrentRefill() {
        TenantId tenantId = new TenantId(1L);
        UserId userId = new UserId(10L);
        UserVO staleUser = user(10L, 1L);
        service.cacheUser(tenantId, staleUser);

        service.invalidateUser(tenantId, userId);
        service.cacheUser(tenantId, staleUser);

        assertNull(service.getCachedUser(tenantId, userId));
        assertFalse(cache.values.containsKey("tenant:1:user:10"));
        assertEquals(Boolean.TRUE, cache.values.get("tenant:1:user:10:refill-fence"));
    }

    private UserVO user(Long id, Long tenantId) {
        return new UserVO().setId(id).setTenantId(tenantId).setUsername("alice");
    }

    private static final class InMemoryCache implements CacheStore {
        private final Map<String, Object> values = new HashMap<>();

        @Override
        public <T> T get(String key, Class<T> type) {
            Object value = values.get(key);
            return value == null ? null : type.cast(value);
        }

        @Override
        public void put(String key, Object value, Duration ttl) {
            values.put(key, value);
        }

        @Override
        public void evict(String key) {
            values.remove(key);
        }
    }
}
