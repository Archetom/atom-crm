package io.github.archetom.crm.infra.external;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataPreview;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataPreviewStore;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded process-local preview cache. Entries hold only the fixed B23 profile and expire eagerly. */
public final class InMemoryEnterpriseDataPreviewStore implements EnterpriseDataPreviewStore {
    private static final int CAPACITY = 2_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, EnterpriseDataPreview> entries = new ConcurrentHashMap<>();

    @Override
    public String issue(EnterpriseDataPreview preview) {
        if (preview == null) throw new IllegalArgumentException("enterprise data preview is required");
        evictExpired(Instant.now());
        if (entries.size() >= CAPACITY) {
            throw new IllegalStateException("enterprise data preview capacity is unavailable");
        }
        for (int attempts = 0; attempts < 4; attempts++) {
            String reference = reference();
            if (entries.putIfAbsent(reference, preview) == null) return reference;
        }
        throw new IllegalStateException("enterprise data preview reference is unavailable");
    }

    @Override
    public Optional<EnterpriseDataPreview> peek(
            TenantId tenantId, MemberId memberId, String reference, Instant now) {
        String key = key(reference);
        return matching(key == null ? null : entries.get(key), tenantId, memberId, reference, now);
    }

    @Override
    public Optional<EnterpriseDataPreview> claim(
            TenantId tenantId, MemberId memberId, String reference, Instant now) {
        String key = key(reference);
        // Removing before matching keeps the consume atomic: two concurrent claims of one
        // reference cannot both succeed, whichever of them the map hands the entry to.
        EnterpriseDataPreview removed = key == null ? null : entries.remove(key);
        return matching(removed, tenantId, memberId, reference, now);
    }

    private static Optional<EnterpriseDataPreview> matching(EnterpriseDataPreview preview,
            TenantId tenantId, MemberId memberId, String reference, Instant now) {
        if (preview == null || tenantId == null || memberId == null || now == null
                || reference == null || !preview.tenantId().sameValueAs(tenantId)
                || !preview.memberId().sameValueAs(memberId) || !preview.expiresAt().isAfter(now)) {
            return Optional.empty();
        }
        return Optional.of(preview);
    }

    /** Returns null for anything that cannot be a reference, so it never reaches the map. */
    private static String key(String reference) {
        return reference != null && reference.matches("[A-Za-z0-9_-]{32,64}") ? reference : null;
    }

    private void evictExpired(Instant now) {
        entries.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private static String reference() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
