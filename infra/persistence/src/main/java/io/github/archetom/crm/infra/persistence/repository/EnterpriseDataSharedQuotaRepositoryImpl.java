package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSharedQuota;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.EnterpriseDataSourceQuotaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

/** MySQL serializes the one source row so quota is shared across tenants and application replicas. */
@Repository
@RequiredArgsConstructor
public class EnterpriseDataSharedQuotaRepositoryImpl implements EnterpriseDataSharedQuota {
    private static final Pattern SOURCE_KEY = Pattern.compile("[A-Z][A-Z0-9_]{2,47}");
    private final EnterpriseDataSourceQuotaMapper mapper;

    @Override public boolean ready() { return true; }

    @Override
    public boolean tryAcquire(TenantId tenantId, String sourceKey, Instant now,
                              int maximumRequests, Duration window) {
        if (tenantId == null || sourceKey == null || !SOURCE_KEY.matcher(sourceKey).matches()
                || now == null || maximumRequests < 1 || window == null || window.isNegative()
                || window.isZero() || window.getNano() != 0 || window.getSeconds() < 1) {
            throw new IllegalArgumentException("enterprise data quota request is invalid");
        }
        long seconds = window.getSeconds();
        Instant windowStart = Instant.ofEpochSecond(Math.floorDiv(now.getEpochSecond(), seconds) * seconds);
        LocalDateTime utcNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        LocalDateTime utcWindowStart = LocalDateTime.ofInstant(windowStart, ZoneOffset.UTC);
        mapper.insertIfAbsent(sourceKey, utcWindowStart, utcNow);
        // Each statement commits independently. The conditional UPDATE is the sole arbitration
        // point, so concurrent first-use inserts cannot form an insert-lock/SELECT FOR UPDATE cycle.
        return mapper.acquireIfAvailable(sourceKey, utcWindowStart, maximumRequests, utcNow) == 1;
    }
}
