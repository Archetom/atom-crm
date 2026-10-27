package io.github.archetom.crm.application.security;

import io.github.archetom.crm.application.port.out.CacheStore;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Optional short-lived authorization cache with tenant-wide revision invalidation. */
@Component
@RequiredArgsConstructor
public class TenantAuthorityCache {

    private static final Duration AUTHORITY_TTL = Duration.ofMinutes(2);
    private static final Duration REVISION_TTL = Duration.ofDays(1);
    private static final String PREFIX = "tenant-authorization:v1:";

    private final CacheStore cacheStore;

    public Lookup find(TenantId tenantId, MemberId memberId) {
        Revision revision = revision(tenantId);
        CachedAuthorities cached = cacheStore.get(authorityKey(tenantId, memberId, revision.value()),
                CachedAuthorities.class);
        return new Lookup(revision.value(), cached == null ? null : Set.copyOf(cached.authorities()));
    }

    public void putIfCurrent(TenantId tenantId, MemberId memberId, String expectedRevision,
                             Set<String> authorities) {
        Revision current = cacheStore.get(revisionKey(tenantId), Revision.class);
        if (current == null || !current.value().equals(expectedRevision)) {
            return;
        }
        cacheStore.put(authorityKey(tenantId, memberId, expectedRevision),
                new CachedAuthorities(authorities.stream().sorted().toList()), AUTHORITY_TTL);
    }

    public void invalidateTenant(TenantId tenantId) {
        cacheStore.put(revisionKey(tenantId), new Revision(UUID.randomUUID().toString()), REVISION_TTL);
    }

    private Revision revision(TenantId tenantId) {
        Revision current = cacheStore.get(revisionKey(tenantId), Revision.class);
        if (current != null) {
            return current;
        }
        Revision created = new Revision(UUID.randomUUID().toString());
        cacheStore.put(revisionKey(tenantId), created, REVISION_TTL);
        return created;
    }

    private String revisionKey(TenantId tenantId) {
        return PREFIX + "tenant:" + tenantId.getValue() + ":revision";
    }

    private String authorityKey(TenantId tenantId, MemberId memberId, String revision) {
        return PREFIX + "tenant:" + tenantId.getValue() + ":actor:" + memberId.getValue()
                + ":revision:" + revision;
    }

    public record Lookup(String revision, Set<String> authorities) {
        public boolean hit() {
            return authorities != null;
        }
    }

    public record Revision(String value) {
    }

    public record CachedAuthorities(List<String> authorities) {
        public CachedAuthorities {
            authorities = authorities == null ? List.of() : List.copyOf(authorities);
        }
    }
}
