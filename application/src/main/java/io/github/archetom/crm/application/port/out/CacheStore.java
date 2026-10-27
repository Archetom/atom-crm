package io.github.archetom.crm.application.port.out;

import java.time.Duration;

/**
 * Output port for an optional application cache.
 *
 * <p>Cache failures must not change business correctness.</p>
 */
public interface CacheStore {

    <T> T get(String key, Class<T> type);

    void put(String key, Object value, Duration ttl);

    void evict(String key);
}
