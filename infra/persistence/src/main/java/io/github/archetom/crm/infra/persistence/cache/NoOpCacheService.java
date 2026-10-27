package io.github.archetom.crm.infra.persistence.cache;

import io.github.archetom.crm.application.port.out.CacheStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cache fallback used when Redis is not enabled.
 */
@Component
@ConditionalOnProperty(name = "atom.redis.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpCacheService implements CacheStore {

    @Override
    public <T> T get(String key, Class<T> type) {
        return null;
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        // Cache is an optional performance optimization.
    }

    @Override
    public void evict(String key) {
        // Cache is an optional performance optimization.
    }
}
