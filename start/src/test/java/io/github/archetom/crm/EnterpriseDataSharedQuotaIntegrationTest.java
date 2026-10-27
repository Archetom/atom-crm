package io.github.archetom.crm;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSharedQuota;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the B23 provider quota is MySQL-arbitrated across independent tenant callers. */
@RequiresDatabase
class EnterpriseDataSharedQuotaIntegrationTest extends BaseIntegrationTest {
    private static final String SOURCE = "GLEIF_TEST";
    private static final Instant NOW = Instant.parse("2026-07-23T11:00:00Z");
    @Autowired private EnterpriseDataSharedQuota quota;

    @Test
    void sourceQuotaIsSharedAcrossTenantsAndResetsOnlyAtItsNextWindow() {
        executeSql("DELETE FROM crm_enterprise_data_source_quota WHERE source_key = '" + SOURCE + "'");

        assertTrue(quota.tryAcquire(new TenantId(101L), SOURCE, NOW, 2, Duration.ofMinutes(1)));
        assertTrue(quota.tryAcquire(new TenantId(202L), SOURCE, NOW.plusSeconds(10), 2, Duration.ofMinutes(1)));
        assertFalse(quota.tryAcquire(new TenantId(303L), SOURCE, NOW.plusSeconds(20), 2, Duration.ofMinutes(1)));
        assertTrue(quota.tryAcquire(new TenantId(404L), SOURCE, NOW.plus(Duration.ofMinutes(1)), 2, Duration.ofMinutes(1)));
    }

    @Test
    void concurrentReservationsCannotExceedTheMySqlLimit() throws Exception {
        executeSql("DELETE FROM crm_enterprise_data_source_quota WHERE source_key = '" + SOURCE + "'");
        try (ExecutorService executor = Executors.newFixedThreadPool(6)) {
            List<Callable<Boolean>> calls = java.util.stream.IntStream.range(0, 6).<Callable<Boolean>>mapToObj(index ->
                    () -> quota.tryAcquire(new TenantId(1000L + index), SOURCE, NOW, 3, Duration.ofMinutes(1))).toList();
            List<Future<Boolean>> results = executor.invokeAll(calls);
            assertEquals(3L, results.stream().filter(result -> {
                try { return result.get(); }
                catch (Exception exception) { throw new IllegalStateException(exception); }
            }).count());
        }
    }

    @Override protected void clearTestData() {
        executeSql("DELETE FROM crm_enterprise_data_source_quota WHERE source_key = '" + SOURCE + "'");
    }
}
