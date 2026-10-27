package io.github.archetom.crm.infra.persistence.cache;

import io.github.archetom.crm.application.port.out.CacheStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Slf4j
@Component
@ConditionalOnProperty(name = "atom.redis.enabled", havingValue = "true")
public class RedisCacheService implements CacheStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                log.debug("Redis cache miss");
                return null;
            }
            log.debug("Redis cache hit");
            return objectMapper.readValue(value, type);
        } catch (Exception exception) {
            log.error("Redis cache get failure: exceptionType={}", exception.getClass().getName());
            return null;
        }
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            if (ttl != null) {
                redisTemplate.opsForValue().set(key, json, ttl);
            } else {
                redisTemplate.opsForValue().set(key, json);
            }
            log.debug("Redis cache write: ttl={}", ttl);
        } catch (Exception exception) {
            log.error("Redis cache write failure: exceptionType={}", exception.getClass().getName());
        }
    }

    @Override
    public void evict(String key) {
        try {
            redisTemplate.delete(key);
            log.debug("Redis cache entry cleared");
        } catch (Exception exception) {
            log.error("Redis cache clear failure: exceptionType={}", exception.getClass().getName());
        }
    }
}
