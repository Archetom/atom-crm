package io.github.archetom.crm.infra.external.readiness;

import io.github.archetom.crm.infra.external.readiness.OfficialSourceHttpSecuritySimulator.AdapterReplica;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceHttpSecuritySimulator.CapturedSinks;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceHttpSecuritySimulator.EvidenceCollector;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceHttpSecuritySimulator.ExecutionEvidence;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceHttpSecuritySimulator.Scenario;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceHttpSecuritySimulator.LeakOracle;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceHttpSecuritySimulator.Operation;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceHttpSecuritySimulator.SafeManifestWriter;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceHttpSecuritySimulator.SecurityConformanceReport;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceHttpSecuritySimulator.SensitiveSink;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceHttpSecuritySimulator.SinkDisposition;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceHttpSecuritySimulator.SinkEvidence;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceHttpSecuritySimulator.SnapshotEffect;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceHttpSecuritySimulator.SyntheticCanaries;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceHttpSecuritySimulator.Tenant;
import io.github.archetom.crm.infra.external.readiness.OfficialSourceHttpSecuritySimulator.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B23-O-L3 executable O2-09/O2-11 scenarios.
 *
 * <p>Every native value is synthetic. Passing this offline lane cannot create production
 * readiness or replace current-build live source conformance.</p>
 */
class OfficialSourceHttpSecuritySimulatorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void o209NormalizesTheCompleteOfflineScenarioMatrixAndPersistsSnapshotEffects() {
        for (Scenario scenario : Scenario.values()) {
            Path scenarioDirectory = temporaryDirectory.resolve(scenario.name());
            World world = new World(scenarioDirectory);
            AdapterReplica firstReplica = world.replica("replica-one");

            ExecutionEvidence evidence = firstReplica.execute(Tenant.ALPHA, scenario);

            assertEquals(scenario.expectedOutcome(), evidence.result().outcome(),
                    scenario.scenarioId());
            assertEquals(scenario.expectedReason(), evidence.result().reason(),
                    scenario.scenarioId());
            assertEquals(scenario.expectedSnapshotEffect(),
                    evidence.result().snapshotEffect(), scenario.scenarioId());
            assertEquals(scenario.expectedObservationCount(),
                    evidence.result().observationCount(), scenario.scenarioId());
            assertEquals(1, evidence.providerCallCount(), scenario.scenarioId());
            assertEquals(0, evidence.retryCount(), scenario.scenarioId());
            assertEquals(0, evidence.redirectFollowCount(), scenario.scenarioId());
            assertEquals(1, evidence.partitionCounter(), scenario.scenarioId());
            assertEquals(1, world.providerCallCount(), scenario.scenarioId());
            assertEquals(0, world.redirectFollowCount(), scenario.scenarioId());

            AdapterReplica rebuiltReplica = world.replica("replica-two-rebuilt");
            boolean expectedReady = scenario.expectedSnapshotEffect()
                    == SnapshotEffect.PRESERVED;
            assertEquals(expectedReady, evidence.currentOperationReadyAfter(),
                    scenario.scenarioId());
            assertTrue(evidence.otherOperationReadyAfter(), scenario.scenarioId());
            assertTrue(evidence.otherTenantOperationReadyAfter(), scenario.scenarioId());
            assertEquals(expectedReady,
                    world.isReady(Tenant.ALPHA, scenario.operation()), scenario.scenarioId());

            Operation otherOperation = scenario.operation() == Operation.EXACT
                    ? Operation.SEARCH : Operation.EXACT;
            assertTrue(world.isReady(Tenant.ALPHA, otherOperation), scenario.scenarioId());
            assertTrue(world.isReady(Tenant.BETA, scenario.operation()), scenario.scenarioId());

            if (!expectedReady) {
                int callsBeforeFailClosedCheck = world.providerCallCount();
                ExecutionEvidence failClosed = rebuiltReplica.execute(Tenant.ALPHA, scenario);
                assertEquals(OfficialSourceHttpSecuritySimulator.SafeOutcome.UNAVAILABLE,
                        failClosed.result().outcome());
                assertEquals(0, failClosed.providerCallCount());
                assertEquals(callsBeforeFailClosedCheck, world.providerCallCount());
            }
        }
    }

    @Test
    void o211InventoriesEverySinkAndWritesAValueFreeMachineReadableManifest()
            throws IOException {
        EvidenceCollector collector = new EvidenceCollector();
        for (Scenario scenario : Scenario.values()) {
            World world = new World(temporaryDirectory.resolve("manifest-" + scenario.name()));
            ExecutionEvidence evidence = world.replica("replica-one")
                    .execute(Tenant.ALPHA, scenario);
            collector.record(evidence);
        }

        SecurityConformanceReport report = collector.report();
        Path manifestPath = SafeManifestWriter.write(
                temporaryDirectory.resolve("b23-o-l3-security-manifest.properties"), report);
        Properties manifest = SafeManifestWriter.read(manifestPath);

        assertEquals("B23-O-L3-security-manifest-v1",
                manifest.getProperty("manifest.schema.version"));
        assertEquals(OfficialSourceHttpSecuritySimulator.FIXTURE_VERSION,
                manifest.getProperty("fixture.version"));
        assertEquals(OfficialSourceHttpSecuritySimulator.LANE_VERSION,
                manifest.getProperty("lane.version"));
        assertEquals(OfficialSourceHttpSecuritySimulator.CONTRACT_VERSION,
                manifest.getProperty("adapter.contract.version"));
        assertEquals(OfficialSourceHttpSecuritySimulator.ADAPTER_BUILD,
                manifest.getProperty("adapter.build"));
        assertEquals("TEST_ONLY", manifest.getProperty("deployment.class"));
        assertEquals("false", manifest.getProperty("production.ready"));
        assertEquals("false", manifest.getProperty("live.evidence.present"));
        assertEquals("false", manifest.getProperty("real.values.persisted"));
        assertEquals("EPHEMERAL_ONLY", manifest.getProperty("usage.mode"));
        assertEquals(Integer.toString(Scenario.values().length),
                manifest.getProperty("scenario.count"));
        assertEquals(Integer.toString(SensitiveSink.values().length),
                manifest.getProperty("sink.count"));
        assertEquals("2", manifest.getProperty("quota.replica.count"));
        assertEquals("20", manifest.getProperty("candidate.limit"));

        Set<String> recordedScenarioIds = indexedValues(
                manifest, "scenario.count", "scenario.", ".id");
        Set<String> expectedScenarioIds = EnumSet.allOf(Scenario.class).stream()
                .map(Scenario::scenarioId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertEquals(expectedScenarioIds, recordedScenarioIds);

        Set<String> recordedSinks = indexedValues(
                manifest, "sink.count", "sink.", ".name");
        Set<String> expectedSinks = EnumSet.allOf(SensitiveSink.class).stream()
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertEquals(expectedSinks, recordedSinks);

        for (int index = 0; index < Scenario.values().length; index++) {
            assertEquals("true", manifest.getProperty("scenario." + index + ".passed"));
            assertEquals("1",
                    manifest.getProperty("scenario." + index + ".provider.call.count"));
            assertEquals("0", manifest.getProperty("scenario." + index + ".retry.count"));
            assertEquals("0",
                    manifest.getProperty("scenario." + index + ".redirect.follow.count"));
            assertEquals("0",
                    manifest.getProperty("scenario." + index + ".leak.hit.count"));
        }
        for (int index = 0; index < SensitiveSink.values().length; index++) {
            assertEquals("0", manifest.getProperty("sink." + index + ".leak.hit.count"));
            assertEquals(Integer.toString(Scenario.values().length),
                    manifest.getProperty("sink." + index + ".inspected.capture.count"));
        }

        byte[] manifestBytes = Files.readAllBytes(manifestPath);
        assertEquals(0, LeakOracle.hitCount(manifestBytes, SyntheticCanaries.all()));
        assertAllTestArtifactsAreValueFree(temporaryDirectory, SyntheticCanaries.all());
    }

    @Test
    void o211FailsClosedForMissingOrLeakingSinkEvidence() {
        List<SinkEvidence> incompleteSinks = new ArrayList<>();
        incompleteSinks.add(new SinkEvidence(
                SensitiveSink.LOG, SinkDisposition.SAFE_TELEMETRY_ONLY, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new SecurityConformanceReport(List.of(), incompleteSinks));

        EvidenceCollector unsafeCollector = new EvidenceCollector();
        for (Scenario scenario : Scenario.values()) {
            World world = new World(temporaryDirectory.resolve("unsafe-" + scenario.name()));
            ExecutionEvidence execution = world.replica("replica-one")
                    .execute(Tenant.ALPHA, scenario);
            if (scenario == Scenario.HTTP_401) {
                CapturedSinks unsafe = execution.capturedSinks();
                unsafe.record(SensitiveSink.LOG, SyntheticCanaries.API_KEY);
                assertTrue(LeakOracle.hitCount(
                        unsafe.capture(SensitiveSink.LOG), SyntheticCanaries.all()) > 0);
            }
            unsafeCollector.record(execution);
        }
        assertThrows(IllegalArgumentException.class, unsafeCollector::report);
    }

    /**
     * Negative controls. The matrix above reports one provider call, no retry and no followed
     * redirect for every scenario; these cases make each of those counters non-zero so the
     * assertions that pin them can fail, and confirm the report refuses the resulting evidence.
     */
    @Test
    void followingARedirectIsCountedAndRefusedByTheReport() {
        World control = new World(temporaryDirectory.resolve("redirect-control"));
        ExecutionEvidence refused = control.replica("replica-one")
                .execute(Tenant.ALPHA, Scenario.REDIRECT);
        assertEquals(OfficialSourceHttpSecuritySimulator.SafeReason.REDIRECT_REJECTED,
                refused.result().reason());
        assertEquals(0, refused.redirectFollowCount());
        assertEquals(0, control.redirectFollowCount());

        // A world per scenario, as the matrix above does: a shared one would revoke EXACT
        // readiness before REDIRECT ran, and the adapter would never reach the provider.
        EvidenceCollector collector = new EvidenceCollector();
        int follows = 0;
        for (Scenario scenario : Scenario.values()) {
            World world = new World(
                    temporaryDirectory.resolve("redirect-followed-" + scenario.name()));
            world.injectRedirectFollowing();
            ExecutionEvidence evidence = world.replica("replica-one")
                    .execute(Tenant.ALPHA, scenario);
            collector.record(evidence);
            follows += evidence.redirectFollowCount();
        }
        assertEquals(1, follows);
        assertThrows(IllegalArgumentException.class, collector::report);
    }

    @Test
    void retryingTheProviderIsCountedAndRefusedByTheReport() {
        World control = new World(temporaryDirectory.resolve("retry-control"));
        ExecutionEvidence once = control.replica("replica-one")
                .execute(Tenant.ALPHA, Scenario.HTTP_500);
        assertEquals(1, once.providerCallCount());
        assertEquals(0, once.retryCount());

        World retried = new World(temporaryDirectory.resolve("retry-injected"));
        retried.injectProviderRetry();
        ExecutionEvidence evidence = retried.replica("replica-one")
                .execute(Tenant.ALPHA, Scenario.HTTP_500);
        assertEquals(2, evidence.providerCallCount());
        assertEquals(1, evidence.retryCount());

        EvidenceCollector collector = new EvidenceCollector();
        int retries = 0;
        for (Scenario scenario : Scenario.values()) {
            World world = new World(
                    temporaryDirectory.resolve("retry-all-" + scenario.name()));
            world.injectProviderRetry();
            ExecutionEvidence recorded = world.replica("replica-one")
                    .execute(Tenant.ALPHA, scenario);
            collector.record(recorded);
            retries += recorded.retryCount();
        }
        // Only the two server-error scenarios are retried.
        assertEquals(2, retries);
        assertThrows(IllegalArgumentException.class, collector::report);
    }

    private Set<String> indexedValues(
            Properties properties, String countKey, String prefix, String suffix) {
        int count = Integer.parseInt(properties.getProperty(countKey));
        java.util.HashSet<String> values = new java.util.HashSet<>();
        for (int index = 0; index < count; index++) {
            values.add(properties.getProperty(prefix + index + suffix));
        }
        return Set.copyOf(values);
    }

    private void assertAllTestArtifactsAreValueFree(Path root, Set<String> canaries)
            throws IOException {
        try (var paths = Files.walk(root)) {
            List<Path> files = paths.filter(Files::isRegularFile).toList();
            assertFalse(files.isEmpty());
            for (Path file : files) {
                byte[] bytes = Files.readAllBytes(file);
                assertEquals(0, LeakOracle.hitCount(bytes, canaries), file.toString());
                String safeText = new String(bytes, StandardCharsets.UTF_8);
                assertFalse(safeText.contains(SyntheticCanaries.API_KEY), file.toString());
            }
        }
    }
}
