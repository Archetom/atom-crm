package io.github.archetom.crm.infra.external.readiness;

import io.github.archetom.crm.infra.external.readiness.OfficialSourceReadinessDurableSimulator.AdmissionProjection;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceReadinessDurableSimulator.Operation;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceReadinessDurableSimulator.OperationResult;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceReadinessDurableSimulator.ProviderOutcome;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceReadinessDurableSimulator.QuotaProfile;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceReadinessDurableSimulator.ReadinessSnapshot;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceReadinessDurableSimulator.SafeOutcome;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceReadinessDurableSimulator.Tenant;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceReadinessDurableSimulator.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B23-O-L2 executable durable quota/readiness scenarios.
 *
 * <p>This layer uses only synthetic source identities. It proves the shared admission and
 * readiness mechanics required by O2-06/07/08/10; it is not live-source evidence and cannot
 * produce a production-positive readiness manifest.</p>
 */
class OfficialSourceReadinessDurableSimulatorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void o206SharesProviderAndTenantAdmissionAcrossTwoReplicasAndPreservesReserves()
            throws Exception {
        World world = world("o2-06", QuotaProfile.standard());

        List<Callable<OperationResult>> alphaSearches = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            int replica = index % 2 + 1;
            alphaSearches.add(() -> world.replica(replica).search(Tenant.ALPHA));
        }
        List<OperationResult> results = invokeTogether(alphaSearches);

        assertEquals(6, count(results, SafeOutcome.SUCCESS));
        assertEquals(6, count(results, SafeOutcome.LIMITED));
        assertEquals(6, world.provider().calls(Tenant.ALPHA, Operation.SEARCH));

        assertOutcome(SafeOutcome.SUCCESS, world.replica(2).search(Tenant.BETA));
        assertOutcome(SafeOutcome.LIMITED, world.replica(1).search(Tenant.BETA));
        assertOutcome(SafeOutcome.SUCCESS, world.replica(1).exact(Tenant.BETA));
        assertOutcome(SafeOutcome.SUCCESS, world.replica(2).exact(Tenant.BETA));
        assertOutcome(SafeOutcome.LIMITED, world.replica(1).exact(Tenant.BETA));
        assertOutcome(SafeOutcome.SUCCESS, world.replica(2).controlProbe(Tenant.BETA));

        AdmissionProjection projection = world.admissionProjection();
        assertEquals(10, projection.providerUsed());
        assertEquals(6, projection.alphaUsed());
        assertEquals(3, projection.betaUsed());
        assertEquals(0, projection.inFlight());
        assertTrue(projection.providerUsed() < QuotaProfile.standard().providerCeiling());
        assertEquals(10, world.provider().totalCalls());

        world.restartReplica(1);
        world.restartReplica(2);
        assertOutcome(SafeOutcome.LIMITED, world.replica(1).search(Tenant.ALPHA));
        assertOutcome(SafeOutcome.LIMITED, world.replica(2).controlProbe(Tenant.BETA));
        assertEquals(10, world.provider().totalCalls());
    }

    @Test
    void o206SharesTheOperationConcurrencySlotAcrossReplicas() throws Exception {
        QuotaProfile singleSlot = new QuotaProfile(Duration.ofMinutes(1),
                60, 10, 6, 2, 1, 1);
        World world = world("o2-06-concurrency", singleSlot);
        var blocker = world.provider().blockNext(Tenant.ALPHA, Operation.SEARCH);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<OperationResult> first = executor.submit(
                    () -> world.replica(1).search(Tenant.ALPHA));
            assertTrue(blocker.awaitEntered());

            assertOutcome(SafeOutcome.LIMITED,
                    world.replica(2).search(Tenant.ALPHA));
            assertEquals(1, world.provider().calls(Tenant.ALPHA, Operation.SEARCH));
            assertEquals(1, world.admissionProjection().inFlight());

            blocker.release();
            assertOutcome(SafeOutcome.SUCCESS, first.get());
            assertEquals(0, world.admissionProjection().inFlight());
        }
    }

    @Test
    void o207UsesNoSourceCallForCapabilityAndNeverRetriesOrFallsBack() {
        World world = world("o2-07", QuotaProfile.standard());

        assertOutcome(SafeOutcome.READY, world.replica(1).capability(Tenant.ALPHA));
        assertOutcome(SafeOutcome.READY, world.replica(2).capability(Tenant.BETA));
        assertEquals(0, world.provider().totalCalls());
        assertEquals(0, world.admissionProjection().providerUsed());

        assertOutcome(SafeOutcome.SUCCESS, world.replica(1).search(Tenant.ALPHA));
        assertEquals(1, world.provider().calls(Tenant.ALPHA, Operation.SEARCH));

        world.provider().enqueue(Tenant.ALPHA, Operation.SEARCH,
                ProviderOutcome.RATE_LIMITED);
        assertOutcome(SafeOutcome.LIMITED, world.replica(2).search(Tenant.ALPHA));
        assertOutcome(SafeOutcome.LIMITED, world.replica(1).search(Tenant.ALPHA));
        assertOutcome(SafeOutcome.LIMITED, world.replica(2).search(Tenant.BETA));
        assertEquals(2, world.provider().calls(Tenant.ALPHA, Operation.SEARCH));
        assertEquals(0, world.provider().calls(Tenant.BETA, Operation.SEARCH));
        assertEquals(Set.of("replica-one", "replica-two"),
                world.provider().replicaIds(Tenant.ALPHA, Operation.SEARCH));
        world.restartReplica(1);
        assertOutcome(SafeOutcome.LIMITED, world.replica(1).search(Tenant.BETA));
        assertEquals(0, world.provider().calls(Tenant.BETA, Operation.SEARCH));

        world.clock().advance(Duration.ofMinutes(1));
        world.provider().enqueue(Tenant.ALPHA, Operation.SEARCH, ProviderOutcome.TIMEOUT);
        assertOutcome(SafeOutcome.UNAVAILABLE, world.replica(2).search(Tenant.ALPHA));
        assertEquals(3, world.provider().calls(Tenant.ALPHA, Operation.SEARCH));

        world.provider().enqueue(Tenant.ALPHA, Operation.EXACT,
                ProviderOutcome.SERVER_ERROR);
        assertOutcome(SafeOutcome.UNAVAILABLE, world.replica(1).exact(Tenant.ALPHA));
        assertEquals(1, world.provider().calls(Tenant.ALPHA, Operation.EXACT));
        assertEquals(Set.of("GLEIF_LEI_API_V1"),
                world.provider().sourceProfiles(Tenant.ALPHA, Operation.SEARCH));

        assertOutcome(SafeOutcome.SUCCESS, world.replica(2).search(Tenant.BETA));

        world.setAdmissionAvailable(false);
        int sourceCallsBeforeFailure = world.provider().totalCalls();
        assertOutcome(SafeOutcome.UNAVAILABLE, world.replica(2).search(Tenant.BETA));
        assertEquals(sourceCallsBeforeFailure, world.provider().totalCalls());
    }

    @Test
    void o208IntersectsFiveMinuteEvidenceAndObservationTtlWithVirtualTime() {
        World world = world("o2-08", QuotaProfile.standard());
        world.publish(Tenant.ALPHA,
                Duration.ofMinutes(4), Duration.ofMinutes(3),
                Duration.ofMinutes(4), Duration.ofMinutes(5),
                Duration.ofMinutes(4), Duration.ofSeconds(45));

        assertOutcome(SafeOutcome.READY, world.replica(1).capability(Tenant.ALPHA));
        OperationResult observed = world.replica(2).exact(Tenant.ALPHA);
        assertOutcome(SafeOutcome.SUCCESS, observed);
        assertEquals(world.clock().instant().plusSeconds(45), observed.observationExpiresAt());

        world.clock().advance(Duration.ofSeconds(45));
        assertFalse(world.clock().instant().isBefore(observed.observationExpiresAt()));
        assertOutcome(SafeOutcome.READY, world.replica(1).capability(Tenant.ALPHA));

        world.clock().advance(Duration.ofMinutes(2).plusSeconds(15));
        assertOutcome(SafeOutcome.NOT_READY, world.replica(2).capability(Tenant.ALPHA));
        int sourceCallsAtExpiry = world.provider().totalCalls();
        assertOutcome(SafeOutcome.UNAVAILABLE, world.replica(1).search(Tenant.ALPHA));
        assertEquals(sourceCallsAtExpiry, world.provider().totalCalls());

        world.publish(Tenant.ALPHA,
                Duration.ofMinutes(4), Duration.ofMinutes(4),
                Duration.ofMinutes(4), Duration.ofMinutes(4),
                Duration.ofMinutes(4), Duration.ofMinutes(4));
        assertOutcome(SafeOutcome.READY, world.replica(2).capability(Tenant.ALPHA));
        world.revoke(Tenant.ALPHA);
        assertOutcome(SafeOutcome.NOT_READY, world.replica(1).capability(Tenant.ALPHA));
        assertOutcome(SafeOutcome.READY, world.replica(2).capability(Tenant.BETA));
    }

    @Test
    void o208FencesInFlightResponsesWhenReadinessEvidenceChanges() throws Exception {
        World revokedWorld = world("o2-08-in-flight-revoke", QuotaProfile.standard());
        var revokeBlocker = revokedWorld.provider().blockNext(Tenant.ALPHA, Operation.EXACT);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<OperationResult> inFlight = executor.submit(
                    () -> revokedWorld.replica(1).exact(Tenant.ALPHA));
            assertTrue(revokeBlocker.awaitEntered());

            revokedWorld.revoke(Tenant.ALPHA);
            assertOutcome(SafeOutcome.NOT_READY,
                    revokedWorld.replica(2).capability(Tenant.ALPHA));
            revokeBlocker.release();

            assertOutcome(SafeOutcome.UNAVAILABLE, inFlight.get());
            assertEquals(1, revokedWorld.provider().totalCalls());
        }

        World rotatedWorld = world("o2-08-in-flight-rotate", QuotaProfile.standard());
        var rotateBlocker = rotatedWorld.provider().blockNext(Tenant.ALPHA, Operation.SEARCH);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<OperationResult> inFlight = executor.submit(
                    () -> rotatedWorld.replica(1).search(Tenant.ALPHA));
            assertTrue(rotateBlocker.awaitEntered());

            rotatedWorld.rotateBinding(
                    Tenant.ALPHA, "policy-alpha-v2", "catalog-alpha-v2");
            rotatedWorld.publishCurrentBinding(Tenant.ALPHA);
            rotateBlocker.release();

            assertOutcome(SafeOutcome.UNAVAILABLE, inFlight.get());
            assertOutcome(SafeOutcome.NOT_READY,
                    rotatedWorld.replica(2).capability(Tenant.ALPHA));
            assertEquals(1, rotatedWorld.provider().totalCalls());
        }
    }

    @Test
    void o208RejectsFutureAndOverflowingEvidenceWithoutSourceIo() {
        World world = world("o2-08-invalid-time", QuotaProfile.standard());
        Instant now = world.clock().instant();
        ReadinessSnapshot futureCheckedAt = world.snapshotAt(Tenant.ALPHA, 2,
                now.plusSeconds(1), now,
                now.plus(Duration.ofMinutes(4)), now.plus(Duration.ofMinutes(4)),
                now.plus(Duration.ofMinutes(4)), now.plus(Duration.ofMinutes(4)),
                Duration.ofMinutes(2), Duration.ofMinutes(2));
        world.publishRaw(Tenant.ALPHA, futureCheckedAt);

        assertOutcome(SafeOutcome.NOT_READY, world.replica(1).capability(Tenant.ALPHA));
        assertEquals(0, world.provider().totalCalls());

        ReadinessSnapshot excessiveAdapterTtl = world.snapshotAt(Tenant.ALPHA, 3,
                now, now, now.plus(Duration.ofMinutes(6)),
                now.plus(Duration.ofMinutes(4)), now.plus(Duration.ofMinutes(4)),
                now.plus(Duration.ofMinutes(4)),
                Duration.ofMinutes(2), Duration.ofMinutes(2));
        world.publishRaw(Tenant.ALPHA, excessiveAdapterTtl);
        assertOutcome(SafeOutcome.NOT_READY, world.replica(2).capability(Tenant.ALPHA));

        world.clock().set(Instant.MAX.minus(Duration.ofMinutes(1)));
        ReadinessSnapshot overflowingCoreLimit = world.snapshotAt(Tenant.ALPHA, 4,
                world.clock().instant(), world.clock().instant(), Instant.MAX,
                Instant.MAX, Instant.MAX, Instant.MAX,
                Duration.ofMinutes(2), Duration.ofMinutes(2));
        world.publishRaw(Tenant.ALPHA, overflowingCoreLimit);

        assertOutcome(SafeOutcome.NOT_READY, world.replica(2).capability(Tenant.ALPHA));
        assertOutcome(SafeOutcome.UNAVAILABLE, world.replica(1).exact(Tenant.ALPHA));
        assertEquals(0, world.provider().totalCalls());
    }

    @Test
    void o210IsolatesBindingCatalogPolicyAndFailureStateAcrossTenantsAndRestarts() {
        World world = world("o2-10", QuotaProfile.standard());

        assertOutcome(SafeOutcome.SUCCESS, world.replica(1).search(Tenant.ALPHA));
        assertOutcome(SafeOutcome.SUCCESS, world.replica(2).search(Tenant.BETA));
        assertEquals(2, world.admissionProjection().providerUsed());

        world.rotateBinding(Tenant.ALPHA, "policy-alpha-v2", "catalog-alpha-v2");
        world.publishCurrentBinding(Tenant.ALPHA);

        int sourceCallsBeforeDriftCheck = world.provider().totalCalls();
        assertOutcome(SafeOutcome.NOT_READY, world.replica(1).capability(Tenant.ALPHA));
        assertOutcome(SafeOutcome.UNAVAILABLE, world.replica(2).exact(Tenant.ALPHA));
        assertEquals(sourceCallsBeforeDriftCheck, world.provider().totalCalls());
        assertOutcome(SafeOutcome.READY, world.replica(1).capability(Tenant.BETA));
        assertOutcome(SafeOutcome.SUCCESS, world.replica(2).exact(Tenant.BETA));

        world.restartReplica(1);
        assertTrue(world.replica(1).replicaId().contains("restarted"));
        assertOutcome(SafeOutcome.READY, world.replica(1).capability(Tenant.ALPHA));
        assertOutcome(SafeOutcome.SUCCESS, world.replica(1).exact(Tenant.ALPHA));

        AdmissionProjection afterRestart = world.admissionProjection();
        assertEquals(4, afterRestart.providerUsed());
        assertEquals(2, afterRestart.alphaUsed());
        assertEquals(2, afterRestart.betaUsed());

        world.setReadinessStoreAvailable(false);
        int callsBeforeStoreFailure = world.provider().totalCalls();
        assertOutcome(SafeOutcome.NOT_READY, world.replica(1).capability(Tenant.ALPHA));
        assertOutcome(SafeOutcome.UNAVAILABLE, world.replica(2).search(Tenant.BETA));
        assertEquals(callsBeforeStoreFailure, world.provider().totalCalls());
    }

    private World world(String name, QuotaProfile quotaProfile) {
        return new World(temporaryDirectory.resolve(name), quotaProfile);
    }

    private List<OperationResult> invokeTogether(List<Callable<OperationResult>> calls)
            throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(calls.size())) {
            List<Future<OperationResult>> futures = executor.invokeAll(calls);
            List<OperationResult> results = new ArrayList<>();
            for (Future<OperationResult> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }

    private long count(List<OperationResult> results, SafeOutcome outcome) {
        return results.stream().filter(result -> result.outcome() == outcome).count();
    }

    private void assertOutcome(SafeOutcome expected, OperationResult result) {
        assertNotNull(result);
        assertEquals(expected, result.outcome());
    }

}
