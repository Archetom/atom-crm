package io.github.archetom.crm.infra.external.readiness;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Test-private B23-O/2 layer-two simulator.
 *
 * <p>Replica objects contain no authoritative quota or readiness state. Each replica opens the
 * same file-backed stores, while the provider simulator remains a separate component with its own
 * call projection. Destroying and rebuilding a replica therefore drops its process-local state
 * without resetting durable admission or readiness evidence.</p>
 */
final class OfficialSourceReadinessDurableSimulator {

    static final Duration CORE_MAX_TTL = Duration.ofMinutes(5);

    private OfficialSourceReadinessDurableSimulator() {
    }

    enum Tenant {
        ALPHA,
        BETA
    }

    enum Operation {
        CAPABILITY,
        SEARCH,
        EXACT,
        CONTROL_PROBE
    }

    enum SafeOutcome {
        READY,
        NOT_READY,
        SUCCESS,
        LIMITED,
        UNAVAILABLE
    }

    enum ProviderOutcome {
        SUCCESS,
        RATE_LIMITED,
        TIMEOUT,
        SERVER_ERROR
    }

    record OperationResult(SafeOutcome outcome, Instant observationExpiresAt) {

        static OperationResult of(SafeOutcome outcome) {
            return new OperationResult(outcome, null);
        }
    }

    record QuotaProfile(
            Duration window,
            int providerCeiling,
            int admittedBudget,
            int tenantBudget,
            int exactReserve,
            int controlReserve,
            int concurrentCalls
    ) implements Serializable {

        QuotaProfile {
            if (window == null || window.isZero() || window.isNegative()
                    || providerCeiling < 1 || admittedBudget < 1
                    || admittedBudget >= providerCeiling || tenantBudget < 1
                    || exactReserve < 1 || controlReserve < 1 || concurrentCalls < 1
                    || exactReserve + controlReserve >= admittedBudget) {
                throw new IllegalArgumentException("quota profile is invalid");
            }
        }

        static QuotaProfile standard() {
            return new QuotaProfile(Duration.ofMinutes(1), 60, 10, 6, 2, 1, 16);
        }

        int searchBoundary() {
            return admittedBudget - exactReserve - controlReserve;
        }

        int exactBoundary() {
            return admittedBudget - controlReserve;
        }
    }

    record BindingManifest(
            String sourceProfile,
            String adapterBuild,
            long bindingRevision,
            String policyDigest,
            String catalogDigest,
            String conformanceDigest
    ) implements Serializable {

        BindingManifest {
            if (sourceProfile == null || sourceProfile.isBlank()
                    || adapterBuild == null || adapterBuild.isBlank()
                    || bindingRevision < 1
                    || policyDigest == null || policyDigest.isBlank()
                    || catalogDigest == null || catalogDigest.isBlank()
                    || conformanceDigest == null || conformanceDigest.isBlank()) {
                throw new IllegalArgumentException("binding manifest is invalid");
            }
        }

        BindingManifest nextRevision(String nextPolicyDigest, String nextCatalogDigest) {
            return new BindingManifest(sourceProfile, adapterBuild, bindingRevision + 1,
                    nextPolicyDigest, nextCatalogDigest, conformanceDigest);
        }
    }

    record ReadinessSnapshot(
            BindingManifest manifest,
            long probeSequence,
            Set<Operation> readyOperations,
            Instant adapterCheckedAt,
            Instant coreReceivedAt,
            Instant adapterExpiresAt,
            Instant rightsValidUntil,
            Instant conformanceValidUntil,
            Instant catalogValidUntil,
            Duration adapterObservationTtl,
            Duration policyObservationTtl,
            boolean revoked
    ) implements Serializable {

        ReadinessSnapshot {
            if (manifest == null || probeSequence < 1 || readyOperations == null
                    || adapterCheckedAt == null || coreReceivedAt == null
                    || adapterExpiresAt == null || rightsValidUntil == null
                    || conformanceValidUntil == null || catalogValidUntil == null
                    || adapterObservationTtl == null || policyObservationTtl == null) {
                throw new IllegalArgumentException("readiness snapshot is incomplete");
            }
            readyOperations = Set.copyOf(readyOperations);
        }

        ReadinessSnapshot revoke() {
            return new ReadinessSnapshot(manifest, probeSequence, readyOperations,
                    adapterCheckedAt, coreReceivedAt, adapterExpiresAt, rightsValidUntil,
                    conformanceValidUntil, catalogValidUntil, adapterObservationTtl,
                    policyObservationTtl, true);
        }
    }

    static final class VirtualClock extends Clock {

        private final AtomicReference<Instant> current;

        VirtualClock(Instant initial) {
            current = new AtomicReference<>(Objects.requireNonNull(initial, "initial"));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("simulator clock is fixed to UTC");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }

        void advance(Duration duration) {
            if (duration == null || duration.isNegative()) {
                throw new IllegalArgumentException("clock advance must not be negative");
            }
            current.updateAndGet(value -> value.plus(duration));
        }

        void set(Instant instant) {
            current.set(Objects.requireNonNull(instant, "instant"));
        }
    }

    static final class World {

        private static final Instant START = Instant.parse("2026-07-20T00:00:00Z");

        private final Path admissionPath;
        private final Path readinessPath;
        private final QuotaProfile quotaProfile;
        private final VirtualClock clock = new VirtualClock(START);
        private final ProviderSimulator provider = new ProviderSimulator();
        private final Map<Tenant, BindingManifest> currentManifests =
                new EnumMap<>(Tenant.class);
        private final Map<Integer, AdapterReplica> replicas = new HashMap<>();

        World(Path directory, QuotaProfile quotaProfile) {
            try {
                Files.createDirectories(directory);
            } catch (IOException exception) {
                throw new IllegalStateException("could not create simulator directory", exception);
            }
            this.admissionPath = directory.resolve("shared-admission.bin");
            this.readinessPath = directory.resolve("readiness-evidence.bin");
            this.quotaProfile = Objects.requireNonNull(quotaProfile, "quotaProfile");
            currentManifests.put(Tenant.ALPHA, initialManifest("alpha"));
            currentManifests.put(Tenant.BETA, initialManifest("beta"));

            ReadinessStore readinessStore = new ReadinessStore(readinessPath);
            currentManifests.forEach(readinessStore::putBinding);
            currentManifests.forEach((tenant, manifest) ->
                    readinessStore.publish(tenant, readySnapshot(tenant, manifest, 1,
                            Duration.ofMinutes(4), Duration.ofMinutes(4),
                            Duration.ofMinutes(4), Duration.ofMinutes(4),
                            Duration.ofMinutes(3), Duration.ofMinutes(2))));
            new AdmissionStore(admissionPath, quotaProfile, clock.instant());
            replicas.put(1, newReplica("replica-one"));
            replicas.put(2, newReplica("replica-two"));
        }

        VirtualClock clock() {
            return clock;
        }

        ProviderSimulator provider() {
            return provider;
        }

        AdapterReplica replica(int number) {
            AdapterReplica replica = replicas.get(number);
            if (replica == null) {
                throw new IllegalArgumentException("unknown simulator replica");
            }
            return replica;
        }

        void restartReplica(int number) {
            String replicaId = switch (number) {
                case 1 -> "replica-one-restarted";
                case 2 -> "replica-two-restarted";
                default -> throw new IllegalArgumentException("unknown simulator replica");
            };
            replicas.put(number, newReplica(replicaId));
        }

        void setAdmissionAvailable(boolean available) {
            new AdmissionStore(admissionPath, quotaProfile, clock.instant())
                    .setAvailable(available);
        }

        void setReadinessStoreAvailable(boolean available) {
            new ReadinessStore(readinessPath).setAvailable(available);
        }

        void revoke(Tenant tenant) {
            new ReadinessStore(readinessPath).revoke(tenant);
        }

        void publish(
                Tenant tenant,
                Duration adapterTtl,
                Duration rightsTtl,
                Duration conformanceTtl,
                Duration catalogTtl,
                Duration adapterObservationTtl,
                Duration policyObservationTtl) {
            BindingManifest manifest = currentManifests.get(tenant);
            ReadinessStore store = new ReadinessStore(readinessPath);
            long nextSequence = store.nextProbeSequence(tenant);
            store.publish(tenant, readySnapshot(tenant, manifest, nextSequence, adapterTtl,
                    rightsTtl, conformanceTtl, catalogTtl,
                    adapterObservationTtl, policyObservationTtl));
        }

        void publishRaw(Tenant tenant, ReadinessSnapshot snapshot) {
            new ReadinessStore(readinessPath).publish(tenant, snapshot);
        }

        ReadinessSnapshot snapshotAt(
                Tenant tenant,
                long probeSequence,
                Instant checkedAt,
                Instant receivedAt,
                Instant adapterExpiresAt,
                Instant rightsValidUntil,
                Instant conformanceValidUntil,
                Instant catalogValidUntil,
                Duration adapterObservationTtl,
                Duration policyObservationTtl) {
            return new ReadinessSnapshot(currentManifests.get(tenant), probeSequence,
                    Set.of(Operation.CAPABILITY, Operation.SEARCH, Operation.EXACT,
                            Operation.CONTROL_PROBE),
                    checkedAt, receivedAt, adapterExpiresAt, rightsValidUntil,
                    conformanceValidUntil, catalogValidUntil,
                    adapterObservationTtl, policyObservationTtl, false);
        }

        void rotateBinding(Tenant tenant, String policyDigest, String catalogDigest) {
            BindingManifest next = currentManifests.get(tenant)
                    .nextRevision(policyDigest, catalogDigest);
            currentManifests.put(tenant, next);
            new ReadinessStore(readinessPath).putBinding(tenant, next);
        }

        void publishCurrentBinding(Tenant tenant) {
            publish(tenant, Duration.ofMinutes(4), Duration.ofMinutes(4),
                    Duration.ofMinutes(4), Duration.ofMinutes(4),
                    Duration.ofMinutes(3), Duration.ofMinutes(2));
        }

        AdmissionProjection admissionProjection() {
            return new AdmissionStore(admissionPath, quotaProfile, clock.instant())
                    .projection(clock.instant());
        }

        private AdapterReplica newReplica(String replicaId) {
            return new AdapterReplica(replicaId, Map.copyOf(currentManifests), clock,
                    new ReadinessStore(readinessPath),
                    new AdmissionStore(admissionPath, quotaProfile, clock.instant()), provider);
        }

        private ReadinessSnapshot readySnapshot(
                Tenant tenant,
                BindingManifest manifest,
                long probeSequence,
                Duration adapterTtl,
                Duration rightsTtl,
                Duration conformanceTtl,
                Duration catalogTtl,
                Duration adapterObservationTtl,
                Duration policyObservationTtl) {
            Instant now = clock.instant();
            return snapshotAt(tenant, probeSequence, now, now, safePlus(now, adapterTtl),
                    safePlus(now, rightsTtl), safePlus(now, conformanceTtl),
                    safePlus(now, catalogTtl), adapterObservationTtl,
                    policyObservationTtl);
        }

        private BindingManifest initialManifest(String partition) {
            return new BindingManifest("GLEIF_LEI_API_V1", "build-o2-l2-1", 1,
                    "policy-" + partition + "-v1", "catalog-" + partition + "-v1",
                    "conformance-o2-l2-v1");
        }
    }

    static final class AdapterReplica {

        private final String replicaId;
        private final Map<Tenant, BindingManifest> manifests;
        private final VirtualClock clock;
        private final ReadinessStore readinessStore;
        private final AdmissionStore admissionStore;
        private final ProviderSimulator provider;

        private AdapterReplica(
                String replicaId,
                Map<Tenant, BindingManifest> manifests,
                VirtualClock clock,
                ReadinessStore readinessStore,
                AdmissionStore admissionStore,
                ProviderSimulator provider) {
            this.replicaId = replicaId;
            this.manifests = manifests;
            this.clock = clock;
            this.readinessStore = readinessStore;
            this.admissionStore = admissionStore;
            this.provider = provider;
        }

        String replicaId() {
            return replicaId;
        }

        OperationResult capability(Tenant tenant) {
            return readinessStore.readyEvidence(tenant, manifest(tenant),
                    Operation.CAPABILITY, clock.instant()) != null
                    ? OperationResult.of(SafeOutcome.READY)
                    : OperationResult.of(SafeOutcome.NOT_READY);
        }

        OperationResult search(Tenant tenant) {
            return execute(tenant, Operation.SEARCH);
        }

        OperationResult exact(Tenant tenant) {
            return execute(tenant, Operation.EXACT);
        }

        OperationResult controlProbe(Tenant tenant) {
            return execute(tenant, Operation.CONTROL_PROBE);
        }

        private OperationResult execute(Tenant tenant, Operation operation) {
            BindingManifest manifest = manifest(tenant);
            Instant now = clock.instant();
            ReadinessSnapshot evidence = readinessStore.readyEvidence(
                    tenant, manifest, operation, now);
            if (evidence == null) {
                return OperationResult.of(SafeOutcome.UNAVAILABLE);
            }
            AdmissionDecision decision = admissionStore.acquire(tenant, operation, now);
            if (decision != AdmissionDecision.GRANTED) {
                return OperationResult.of(decision == AdmissionDecision.LIMITED
                        ? SafeOutcome.LIMITED : SafeOutcome.UNAVAILABLE);
            }
            try {
                ProviderOutcome providerOutcome = provider.call(
                        tenant, operation, manifest.sourceProfile(), replicaId);
                return switch (providerOutcome) {
                    case SUCCESS -> success(tenant, manifest, operation, evidence);
                    case RATE_LIMITED -> {
                        admissionStore.cooldownUntilWindowEnd(now);
                        yield OperationResult.of(SafeOutcome.LIMITED);
                    }
                    case TIMEOUT, SERVER_ERROR ->
                            OperationResult.of(SafeOutcome.UNAVAILABLE);
                };
            } finally {
                admissionStore.release(operation);
            }
        }

        private OperationResult success(
                Tenant tenant,
                BindingManifest manifest,
                Operation operation,
                ReadinessSnapshot evidence) {
            Instant now = clock.instant();
            if (!readinessStore.isCurrentReadyEvidence(
                    tenant, manifest, operation, now, evidence)) {
                return OperationResult.of(SafeOutcome.UNAVAILABLE);
            }
            if (operation == Operation.CONTROL_PROBE) {
                return OperationResult.of(SafeOutcome.SUCCESS);
            }
            Instant expiresAt = minimum(
                    safePlus(now, CORE_MAX_TTL),
                    safePlus(now, evidence.adapterObservationTtl()),
                    safePlus(now, evidence.policyObservationTtl()));
            return new OperationResult(SafeOutcome.SUCCESS, expiresAt);
        }

        private BindingManifest manifest(Tenant tenant) {
            BindingManifest manifest = manifests.get(tenant);
            if (manifest == null) {
                throw new IllegalArgumentException("tenant is not bound to this replica");
            }
            return manifest;
        }
    }

    record AdmissionProjection(
            int providerUsed,
            int alphaUsed,
            int betaUsed,
            int inFlight,
            Instant windowStartedAt,
            boolean available
    ) {
    }

    private enum AdmissionDecision {
        GRANTED,
        LIMITED,
        UNAVAILABLE
    }

    private static final class AdmissionStore {

        private final DurableFile<AdmissionState> file;
        private final QuotaProfile profile;

        private AdmissionStore(Path path, QuotaProfile profile, Instant initialWindow) {
            this.profile = profile;
            file = new DurableFile<>(path, () -> new AdmissionState(initialWindow));
            file.read();
        }

        AdmissionDecision acquire(Tenant tenant, Operation operation, Instant now) {
            return file.update(state -> {
                if (!state.available) {
                    return AdmissionDecision.UNAVAILABLE;
                }
                if (now.isBefore(state.windowStartedAt)) {
                    return AdmissionDecision.UNAVAILABLE;
                }
                resetExpiredWindow(state, now);
                if (state.providerCooldownUntil != null
                        && now.isBefore(state.providerCooldownUntil)) {
                    return AdmissionDecision.LIMITED;
                }
                state.providerCooldownUntil = null;
                if (operation != Operation.CONTROL_PROBE
                        && state.tenantUsed.getOrDefault(tenant, 0)
                        >= profile.tenantBudget()) {
                    return AdmissionDecision.LIMITED;
                }
                int operationBoundary = switch (operation) {
                    case SEARCH -> profile.searchBoundary();
                    case EXACT -> profile.exactBoundary();
                    case CONTROL_PROBE -> profile.admittedBudget();
                    case CAPABILITY -> throw new IllegalArgumentException(
                            "capability must not consume admission");
                };
                if (state.providerUsed >= operationBoundary
                        || state.inFlight >= profile.concurrentCalls()) {
                    return AdmissionDecision.LIMITED;
                }
                state.providerUsed++;
                state.inFlight++;
                if (operation != Operation.CONTROL_PROBE) {
                    state.tenantUsed.merge(tenant, 1, Integer::sum);
                }
                return AdmissionDecision.GRANTED;
            });
        }

        void release(Operation operation) {
            file.update(state -> {
                if (state.inFlight < 1) {
                    throw new IllegalStateException("admission slot released more than once");
                }
                state.inFlight--;
                return null;
            });
        }

        void cooldownUntilWindowEnd(Instant now) {
            file.update(state -> {
                resetExpiredWindow(state, now);
                state.providerCooldownUntil = safePlus(
                        state.windowStartedAt, profile.window());
                return null;
            });
        }

        void setAvailable(boolean available) {
            file.update(state -> {
                state.available = available;
                return null;
            });
        }

        AdmissionProjection projection(Instant now) {
            return file.update(state -> {
                resetExpiredWindow(state, now);
                return new AdmissionProjection(state.providerUsed,
                        state.tenantUsed.getOrDefault(Tenant.ALPHA, 0),
                        state.tenantUsed.getOrDefault(Tenant.BETA, 0),
                        state.inFlight, state.windowStartedAt, state.available);
            });
        }

        private void resetExpiredWindow(AdmissionState state, Instant now) {
            Instant windowEnd = safePlus(state.windowStartedAt, profile.window());
            if (!now.isBefore(windowEnd)) {
                state.windowStartedAt = now;
                state.providerUsed = 0;
                state.tenantUsed.clear();
                state.providerCooldownUntil = null;
            }
        }
    }

    private static final class AdmissionState implements Serializable {

        private Instant windowStartedAt;
        private int providerUsed;
        private int inFlight;
        private boolean available = true;
        private final Map<Tenant, Integer> tenantUsed = new EnumMap<>(Tenant.class);
        private Instant providerCooldownUntil;

        private AdmissionState(Instant windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }
    }

    private static final class ReadinessStore {

        private final DurableFile<ReadinessState> file;

        private ReadinessStore(Path path) {
            file = new DurableFile<>(path, ReadinessState::new);
            file.read();
        }

        void putBinding(Tenant tenant, BindingManifest manifest) {
            file.update(state -> {
                BindingManifest current = state.bindings.get(tenant);
                if (current != null && manifest.bindingRevision() < current.bindingRevision()) {
                    throw new IllegalArgumentException("binding revision must not move backwards");
                }
                state.bindings.put(tenant, manifest);
                return null;
            });
        }

        void publish(Tenant tenant, ReadinessSnapshot snapshot) {
            file.update(state -> {
                ReadinessSnapshot current = state.snapshots.get(tenant);
                if (current != null && snapshot.probeSequence() <= current.probeSequence()) {
                    throw new IllegalArgumentException("probe sequence must increase");
                }
                state.snapshots.put(tenant, snapshot);
                return null;
            });
        }

        long nextProbeSequence(Tenant tenant) {
            ReadinessSnapshot current = file.read().snapshots.get(tenant);
            return current == null ? 1 : current.probeSequence() + 1;
        }

        void revoke(Tenant tenant) {
            file.update(state -> {
                ReadinessSnapshot current = state.snapshots.get(tenant);
                if (current != null) {
                    state.snapshots.put(tenant, current.revoke());
                }
                return null;
            });
        }

        void setAvailable(boolean available) {
            file.update(state -> {
                state.available = available;
                return null;
            });
        }

        ReadinessSnapshot readyEvidence(
                Tenant tenant,
                BindingManifest replicaManifest,
                Operation operation,
                Instant now) {
            ReadinessState state = file.read();
            return readyEvidence(state, tenant, replicaManifest, operation, now);
        }

        boolean isCurrentReadyEvidence(
                Tenant tenant,
                BindingManifest replicaManifest,
                Operation operation,
                Instant now,
                ReadinessSnapshot evidence) {
            ReadinessState state = file.read();
            ReadinessSnapshot current = readyEvidence(
                    state, tenant, replicaManifest, operation, now);
            return evidence.equals(current);
        }

        private ReadinessSnapshot readyEvidence(
                ReadinessState state,
                Tenant tenant,
                BindingManifest replicaManifest,
                Operation operation,
                Instant now) {
            if (!state.available) {
                return null;
            }
            BindingManifest currentBinding = state.bindings.get(tenant);
            ReadinessSnapshot snapshot = state.snapshots.get(tenant);
            if (currentBinding == null || snapshot == null || snapshot.revoked()
                    || !currentBinding.equals(replicaManifest)
                    || !currentBinding.equals(snapshot.manifest())
                    || !snapshot.readyOperations().contains(operation)
                    || snapshot.adapterObservationTtl().isZero()
                    || snapshot.adapterObservationTtl().isNegative()
                    || snapshot.policyObservationTtl().isZero()
                    || snapshot.policyObservationTtl().isNegative()
                    || snapshot.adapterCheckedAt().isAfter(now)
                    || snapshot.coreReceivedAt().isAfter(now)) {
                return null;
            }
            try {
                Instant adapterHardLimit = snapshot.adapterCheckedAt().plus(CORE_MAX_TTL);
                if (!snapshot.adapterExpiresAt().isAfter(snapshot.adapterCheckedAt())
                        || snapshot.adapterExpiresAt().isAfter(adapterHardLimit)) {
                    return null;
                }
                Instant effectiveExpiry = minimum(snapshot.adapterExpiresAt(),
                        snapshot.coreReceivedAt().plus(CORE_MAX_TTL),
                        snapshot.rightsValidUntil(), snapshot.conformanceValidUntil(),
                        snapshot.catalogValidUntil());
                return now.isBefore(effectiveExpiry) ? snapshot : null;
            } catch (DateTimeException | ArithmeticException exception) {
                return null;
            }
        }
    }

    private static final class ReadinessState implements Serializable {

        private boolean available = true;
        private final Map<Tenant, BindingManifest> bindings = new EnumMap<>(Tenant.class);
        private final Map<Tenant, ReadinessSnapshot> snapshots = new EnumMap<>(Tenant.class);
    }

    static final class ProviderSimulator {

        private final Map<CallKey, AtomicInteger> calls = new ConcurrentHashMap<>();
        private final Map<FailureKey, ConcurrentLinkedQueue<ProviderOutcome>> outcomes =
                new ConcurrentHashMap<>();
        private final AtomicReference<BlockingCall> blockingCall = new AtomicReference<>();

        ProviderOutcome call(
                Tenant tenant, Operation operation, String sourceProfile, String replicaId) {
            if (operation == Operation.CAPABILITY) {
                throw new IllegalArgumentException("capability must not call provider");
            }
            CallKey key = new CallKey(tenant, operation, sourceProfile, replicaId);
            calls.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
            BlockingCall blocker = blockingCall.get();
            if (blocker != null && blocker.matches(tenant, operation)
                    && blocker.claimed.compareAndSet(false, true)) {
                blocker.entered.countDown();
                try {
                    if (!blocker.release.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("provider simulator was not released");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return ProviderOutcome.TIMEOUT;
                } finally {
                    blockingCall.compareAndSet(blocker, null);
                }
            }
            ConcurrentLinkedQueue<ProviderOutcome> queue = outcomes.get(
                    new FailureKey(tenant, operation));
            ProviderOutcome outcome = queue == null ? null : queue.poll();
            return outcome == null ? ProviderOutcome.SUCCESS : outcome;
        }

        void enqueue(Tenant tenant, Operation operation, ProviderOutcome outcome) {
            outcomes.computeIfAbsent(new FailureKey(tenant, operation),
                    ignored -> new ConcurrentLinkedQueue<>()).add(outcome);
        }

        BlockingCall blockNext(Tenant tenant, Operation operation) {
            BlockingCall blocker = new BlockingCall(tenant, operation);
            if (!blockingCall.compareAndSet(null, blocker)) {
                throw new IllegalStateException("a provider call is already blocked");
            }
            return blocker;
        }

        int calls(Tenant tenant, Operation operation) {
            return calls.entrySet().stream()
                    .filter(entry -> entry.getKey().tenant() == tenant
                            && entry.getKey().operation() == operation)
                    .mapToInt(entry -> entry.getValue().get())
                    .sum();
        }

        int totalCalls() {
            return calls.values().stream().mapToInt(AtomicInteger::get).sum();
        }

        Set<String> sourceProfiles(Tenant tenant, Operation operation) {
            return calls.keySet().stream()
                    .filter(key -> key.tenant() == tenant && key.operation() == operation)
                    .map(CallKey::sourceProfile)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        Set<String> replicaIds(Tenant tenant, Operation operation) {
            return calls.keySet().stream()
                    .filter(key -> key.tenant() == tenant && key.operation() == operation)
                    .map(CallKey::replicaId)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        private record CallKey(
                Tenant tenant, Operation operation, String sourceProfile, String replicaId) {
        }

        private record FailureKey(Tenant tenant, Operation operation) {
        }

        static final class BlockingCall {

            private final Tenant tenant;
            private final Operation operation;
            private final AtomicBoolean claimed = new AtomicBoolean();
            private final CountDownLatch entered = new CountDownLatch(1);
            private final CountDownLatch release = new CountDownLatch(1);

            private BlockingCall(Tenant tenant, Operation operation) {
                this.tenant = tenant;
                this.operation = operation;
            }

            boolean awaitEntered() {
                try {
                    return entered.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            void release() {
                release.countDown();
            }

            private boolean matches(Tenant candidateTenant, Operation candidateOperation) {
                return tenant == candidateTenant && operation == candidateOperation;
            }
        }
    }

    private static final class DurableFile<T extends Serializable> {

        private static final Map<Path, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();

        private final Path path;
        private final Path lockPath;
        private final Supplier<T> initialState;

        private DurableFile(Path path, Supplier<T> initialState) {
            this.path = path.toAbsolutePath().normalize();
            this.lockPath = this.path.resolveSibling(this.path.getFileName() + ".lock");
            this.initialState = initialState;
        }

        T read() {
            return withLock(this::readUnlocked);
        }

        <R> R update(Function<T, R> mutation) {
            return withLock(() -> {
                T state = readUnlocked();
                R result = mutation.apply(state);
                writeUnlocked(state);
                return result;
            });
        }

        private <R> R withLock(Supplier<R> action) {
            ReentrantLock jvmLock = JVM_LOCKS.computeIfAbsent(lockPath,
                    ignored -> new ReentrantLock());
            jvmLock.lock();
            try {
                Files.createDirectories(path.getParent());
                try (FileChannel channel = FileChannel.open(lockPath,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                     FileLock ignored = channel.lock()) {
                    return action.get();
                }
            } catch (IOException exception) {
                throw new IllegalStateException("durable simulator store is unavailable", exception);
            } finally {
                jvmLock.unlock();
            }
        }

        @SuppressWarnings("unchecked")
        private T readUnlocked() {
            if (!Files.exists(path)) {
                T initial = initialState.get();
                writeUnlocked(initial);
                return initial;
            }
            try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(path))) {
                return (T) input.readObject();
            } catch (IOException | ClassNotFoundException exception) {
                throw new IllegalStateException("durable simulator state could not be read", exception);
            }
        }

        private void writeUnlocked(T state) {
            try {
                Path temporary = Files.createTempFile(path.getParent(),
                        path.getFileName().toString(), ".commit");
                try {
                    try (ObjectOutputStream output = new ObjectOutputStream(
                            Files.newOutputStream(temporary, StandardOpenOption.TRUNCATE_EXISTING))) {
                        output.writeObject(state);
                        output.flush();
                    }
                    try (FileChannel channel = FileChannel.open(temporary,
                            StandardOpenOption.WRITE)) {
                        channel.force(true);
                    }
                    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("durable simulator state could not commit", exception);
            }
        }
    }

    private static Instant safePlus(Instant instant, Duration duration) {
        try {
            return instant.plus(duration);
        } catch (DateTimeException | ArithmeticException exception) {
            return Instant.MAX;
        }
    }

    private static Instant minimum(Instant first, Instant... remaining) {
        Instant minimum = first;
        for (Instant candidate : remaining) {
            if (candidate.isBefore(minimum)) {
                minimum = candidate;
            }
        }
        return minimum;
    }
}
