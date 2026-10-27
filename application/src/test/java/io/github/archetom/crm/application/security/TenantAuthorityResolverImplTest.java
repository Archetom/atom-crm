package io.github.archetom.crm.application.security;

import io.github.archetom.crm.application.port.out.CacheStore;
import io.github.archetom.crm.domain.entity.RoleGrant;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.archetom.crm.domain.policy.AuthorityCatalog.USERS_READ;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantAuthorityResolverImplTest {

    @Test
    void cachesByTenantAndActorAndReevaluatesAfterTenantInvalidation() {
        TenantAuthorizationEvaluator evaluator = mock(TenantAuthorizationEvaluator.class);
        TenantId tenantId = new TenantId(9L);
        MemberId memberId = new MemberId(7L);
        TenantAuthorizationEvaluator.Evaluation evaluation = new TenantAuthorizationEvaluator.Evaluation(
                mock(io.github.archetom.crm.domain.entity.OrganizationMembership.class), List.of(),
                List.of(RoleGrant.withScope(USERS_READ, DataScope.SELF)));
        when(evaluation.membership().isActive()).thenReturn(true);
        when(evaluator.evaluate(tenantId, memberId)).thenReturn(evaluation);
        TenantAuthorityCache cache = new TenantAuthorityCache(new InMemoryCacheStore());
        TenantAuthorityResolverImpl resolver = new TenantAuthorityResolverImpl(evaluator, cache);

        assertEquals(Set.of(USERS_READ), resolver.resolve(7L, 9L));
        assertEquals(Set.of(USERS_READ), resolver.resolve(7L, 9L));
        cache.invalidateTenant(tenantId);
        assertEquals(Set.of(USERS_READ), resolver.resolve(7L, 9L));

        verify(evaluator, times(2)).evaluate(tenantId, memberId);
    }

    private static final class InMemoryCacheStore implements CacheStore {
        private final Map<String, Object> values = new ConcurrentHashMap<>();

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
