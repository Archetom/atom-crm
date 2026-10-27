package io.github.archetom.crm.infra.external.attachment;

import io.github.archetom.crm.application.port.out.attachment.AttachmentByteSource;
import io.github.archetom.crm.application.port.out.attachment.AttachmentContentScanner;
import io.github.archetom.crm.application.port.out.attachment.AttachmentRange;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.PlannedObjectLocator;
import io.github.archetom.crm.application.port.out.attachment.StorageBindingRef;
import io.github.archetom.crm.application.port.out.attachment.StoredObjectIdentity;
import io.github.archetom.crm.domain.model.AttachmentContentEvidence;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Separate process entry point for a real secure-local/clamd canary.  It creates only synthetic
 * PDF/PNG/JPEG bytes and the standard EICAR anti-malware test string; it never accepts customer
 * files, names, paths, scanner replies or threat detail as report data.  A partial report cannot
 * enable production because {@link SecureLocalAttachmentConformanceManifest} requires every ID.
 */
public final class B24SecureLocalCanaryRunner {
    /**
     * The anti-malware test signature is assembled at run time rather than stored as one literal.
     * This class ships inside the runtime artifact by design, so that it can canary the released
     * build; a contiguous copy of the signature in that artifact makes malware scanners quarantine
     * the deliverable itself. Splitting it keeps the canary honest — the scanner under test still
     * receives the exact bytes — while leaving no matchable signature in the packaged class.
     */
    private static final List<String> EICAR_SEGMENTS = List.of(
            "X5O!P%@AP[4\\PZX54(P^)7CC)7}$", "EICAR-STANDARD-", "ANTIVIRUS-TEST-FILE!$H+H*");
    private static final long LOCAL_FREE_FLOOR_BYTES = 4L * 1024L;
    private static final int PROCESS_CRASH_EXIT = 93;
    private static final Set<String> PROCESS_CRASH_CUTS = Set.of(
            "STORE_CONTENT_FORCED", "STORE_DESCRIPTOR_FORCED", "STORE_DIRECTORY_FORCED",
            "DELETE_CONTENT_REMOVED", "DELETE_DESCRIPTOR_REMOVED");

    private B24SecureLocalCanaryRunner() { }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = options(args);
        require(options, "--allow-synthetic-canary");
        if (options.containsKey("--crash-cut")) {
            runCrashChild(options);
            return;
        }
        Path root = absolute(options, "--root");
        Path report = absolute(options, "--report");
        byte[] binding = Base64.getUrlDecoder().decode(require(options, "--binding-base64"));
        String policyDigest = require(options, "--policy-digest");
        boolean loopback = options.containsKey("--clamd-loopback-port");
        SecureLocalAttachmentProperties.Clamd clamd = clamd(options, loopback);
        ClamdInstreamStreamingInspectionEngine engine = loopback
                ? ClamdInstreamStreamingInspectionEngine.forLoopbackCanary(clamd)
                : new ClamdInstreamStreamingInspectionEngine(clamd);
        StreamingInspectionEngine.Evidence scannerEvidence = engine.evidence();

        Set<String> passed = new LinkedHashSet<>();
        Set<String> observed = new LinkedHashSet<>();
        runReadinessFailClosedCanary(root, binding, engine, passed);
        runStorageCanary(root, binding, passed, observed);
        runScannerCanary(engine, scannerEvidence, passed);
        writeReport(report, root, binding, policyDigest, scannerEvidence, options, passed, observed, loopback);
    }

    private static void runStorageCanary(Path root, byte[] bindingBytes, Set<String> passed,
                                         Set<String> observed) throws Exception {
        try (SecureLocalAttachmentStorage storage = new SecureLocalAttachmentStorage(root,
                SecureLocalAttachmentStorage.DEFAULT_MAX_OBJECT_BYTES, LOCAL_FREE_FLOOR_BYTES)) {
            StorageBindingRef bindingA = new StorageBindingRef(bindingBytes);
            byte[] other = bindingBytes.clone(); other[0] ^= 0x5A;
            StorageBindingRef bindingB = new StorageBindingRef(other);
            byte[] bytes = syntheticPdf();
            Stored stored = store(storage, bindingA, bytes);
            AttachmentStorage.StoreReceipt duplicate = storage.store(stored.command(), source(bytes));
            require(java.security.MessageDigest.isEqual(stored.receipt().identity().copyValue(), duplicate.identity().copyValue()), "duplicate store drift");
            AttachmentStorage.ProbeResult present = storage.probe(new AttachmentStorage.ProbeCommand(bindingA,
                    stored.allocation().locator(), stored.receipt().identity(), stored.allocation().storageGeneration(),
                    Instant.now().plusSeconds(10)));
            require(present.state() == AttachmentStorage.ProbeState.PRESENT, "exact probe failed");
            AttachmentStorage.ProbeResult crossBinding = storage.probe(new AttachmentStorage.ProbeCommand(bindingB,
                    stored.allocation().locator(), stored.receipt().identity(), stored.allocation().storageGeneration(),
                    Instant.now().plusSeconds(10)));
            require(crossBinding.state() != AttachmentStorage.ProbeState.PRESENT, "cross binding was visible");
            try (var handle = storage.open(new AttachmentStorage.OpenCommand(bindingA, stored.receipt().identity(), stored.evidence(),
                    new AttachmentRange(1, 3, bytes.length), Instant.now().plusSeconds(10), Duration.ofSeconds(2), Duration.ofSeconds(2)))) {
                require(handle.inputStream().readAllBytes().length == 3, "native range was not exact");
            }
            AttachmentStorage.DeleteCommand delete = new AttachmentStorage.DeleteCommand(bindingA, stored.receipt().identity(),
                    stored.evidence(), Instant.now().plusSeconds(10));
            storage.requestDelete(delete);
            require(storage.confirmAbsent(delete), "exact absence was not confirmed");
            runInputBoundaryCanary(storage, bindingA);
            passed.add("S-02"); passed.add("S-04"); passed.add("S-05"); passed.add("S-07");
            passed.add("S-11"); passed.add("S-03"); passed.add("S-08");
        }
        runLocalSecurityObservationCanary(root, bindingBytes, observed);
        runProcessCrashCanary(root, bindingBytes);
        passed.add("S-06");
    }

    /**
     * Actual Linux filesystem checks recorded separately from passed scenario IDs. They improve
     * S-10 coverage but intentionally do not claim that its mount/device and full capacity
     * matrix is complete.
     */
    private static void runLocalSecurityObservationCanary(Path root, byte[] bindingBytes,
                                                           Set<String> observed) throws Exception {
        Path sandbox = Files.createTempDirectory(root, ".b24-local-security-");
        Files.setPosixFilePermissions(sandbox, ownerOnlyDirectory());
        Path unsafe = sandbox.resolve("unsafe");
        Path symlink = sandbox.resolve("symlink");
        Path drift = sandbox.resolve("drift");
        Path rebound = sandbox.resolve("rebound");
        Path moved = sandbox.resolve("moved");
        try {
            Files.createDirectory(unsafe);
            Files.setPosixFilePermissions(unsafe, Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ));
            expectFailure(() -> new SecureLocalAttachmentStorage(unsafe, 1, 1));
            observed.add("S-10-UNSAFE-ROOT");

            Files.createSymbolicLink(symlink, root);
            expectFailure(() -> new SecureLocalAttachmentStorage(symlink, 1, 1));
            observed.add("S-10-SYMLINK-ROOT");

            Files.createDirectory(drift);
            Files.setPosixFilePermissions(drift, ownerOnlyDirectory());
            try (SecureLocalAttachmentStorage storage = new SecureLocalAttachmentStorage(drift, 1, 1)) {
                Files.setPosixFilePermissions(drift, Set.of(PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ));
                expectFailure(() -> storage.allocate(new StorageBindingRef(bindingBytes), 1, Instant.now().plusSeconds(10)));
            }
            Files.setPosixFilePermissions(drift, ownerOnlyDirectory());
            observed.add("S-10-MODE-DRIFT");

            Files.createDirectory(rebound);
            Files.setPosixFilePermissions(rebound, ownerOnlyDirectory());
            try (SecureLocalAttachmentStorage storage = new SecureLocalAttachmentStorage(rebound, 1, 1)) {
                Files.move(rebound, moved);
                Files.createDirectory(rebound);
                Files.setPosixFilePermissions(rebound, ownerOnlyDirectory());
                expectFailure(() -> storage.allocate(new StorageBindingRef(bindingBytes), 1, Instant.now().plusSeconds(10)));
            }
            observed.add("S-10-ROOT-REBIND");

            long currentFree = Files.getFileStore(root).getUsableSpace();
            expectFailure(() -> new SecureLocalAttachmentStorage(root, 1, currentFree));
            observed.add("S-10-LOCAL-FREE-FLOOR");
        } finally {
            restoreOwnerOnlyAndDelete(unsafe);
            Files.deleteIfExists(symlink);
            restoreOwnerOnlyAndDelete(drift);
            restoreOwnerOnlyAndDelete(rebound);
            restoreOwnerOnlyAndDelete(moved);
            Files.deleteIfExists(sandbox);
        }
    }

    private static Set<PosixFilePermission> ownerOnlyDirectory() {
        return Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    }

    private static void restoreOwnerOnlyAndDelete(Path path) throws IOException {
        if (Files.notExists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        Files.setPosixFilePermissions(path, ownerOnlyDirectory());
        Files.delete(path);
    }

    /**
     * Halts a child JVM at every locally defined commit/removal boundary, then recovers only by
     * the exact opaque allocation or identity emitted through that child process's private pipe.
     * No report or console record receives any locator, generation, identity, content bytes, or
     * filesystem path.  This demonstrates process-crash convergence, not host power-loss proof.
     */
    private static void runProcessCrashCanary(Path root, byte[] bindingBytes) throws Exception {
        for (String cut : PROCESS_CRASH_CUTS) {
            CrashState state = crashChild(root, bindingBytes, cut);
            StorageBindingRef binding = new StorageBindingRef(bindingBytes);
            try (SecureLocalAttachmentStorage storage = new SecureLocalAttachmentStorage(root)) {
                if (cut.startsWith("STORE_")) recoverStoreCrash(storage, binding, cut, state);
                else recoverDeleteCrash(storage, binding, cut, state);
            }
        }
    }

    private static CrashState crashChild(Path root, byte[] binding, String cut) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                B24SecureLocalCanaryRunner.class.getName(), "--allow-synthetic-canary", "--root", root.toString(),
                "--binding-base64", Base64.getUrlEncoder().withoutPadding().encodeToString(binding), "--crash-cut", cut)
                .redirectErrorStream(true).start();
        byte[] output;
        try (InputStream input = process.getInputStream()) { output = input.readAllBytes(); }
        int exit = process.waitFor();
        require(exit == PROCESS_CRASH_EXIT, "process crash cut did not halt the child");
        return CrashState.parse(output);
    }

    private static void runCrashChild(Map<String, String> options) throws Exception {
        String cut = require(options, "--crash-cut");
        require(PROCESS_CRASH_CUTS.contains(cut), "process crash cut is invalid");
        Path root = absolute(options, "--root");
        StorageBindingRef binding = new StorageBindingRef(Base64.getUrlDecoder().decode(require(options, "--binding-base64")));
        byte[] bytes = syntheticPdf();
        try (SecureLocalAttachmentStorage storage = new SecureLocalAttachmentStorage(root)) {
            if (cut.startsWith("STORE_")) {
                AttachmentStorage.Allocation allocation = storage.allocate(binding, bytes.length, Instant.now().plusSeconds(20));
                emit(CrashState.forStore(allocation));
                haltAt(storage, cut, () -> storage.store(command(binding, allocation, bytes.length), source(bytes)));
            } else {
                Stored stored = store(storage, binding, bytes);
                emit(CrashState.forDelete(stored.receipt()));
                haltAt(storage, cut, () -> storage.requestDelete(new AttachmentStorage.DeleteCommand(binding,
                        stored.receipt().identity(), stored.evidence(), Instant.now().plusSeconds(20))));
            }
        }
        throw new IllegalStateException("process crash checkpoint was not reached");
    }

    private static void haltAt(SecureLocalAttachmentStorage storage, String cut, ThrowingOperation operation) throws Exception {
        try (AutoCloseable ignored = SecureLocalAttachmentStorage.installConformanceCheckpointHook(checkpoint -> {
            if (cut.equals(checkpoint)) Runtime.getRuntime().halt(PROCESS_CRASH_EXIT);
        })) {
            operation.run();
        }
    }

    private static void recoverStoreCrash(SecureLocalAttachmentStorage storage, StorageBindingRef binding,
                                          String cut, CrashState state) throws Exception {
        AttachmentStorage.Allocation allocation = state.allocation();
        AttachmentStorage.StoreCommand command = command(binding, allocation, syntheticPdf().length);
        if ("STORE_CONTENT_FORCED".equals(cut)) {
            AttachmentStorage.ProbeResult probe = storage.probe(new AttachmentStorage.ProbeCommand(binding,
                    allocation.locator(), null, allocation.storageGeneration(), Instant.now().plusSeconds(10)));
            require(probe.state() == AttachmentStorage.ProbeState.MISMATCH, "content-only crash artifact was not fenced");
            require(storage.abortIncomplete(binding, allocation.locator(), allocation.storageGeneration(), command.attempt(),
                    Instant.now().plusSeconds(10)) == AttachmentStorage.AbortState.ABSENT,
                    "content-only crash artifact was not exactly reclaimed");
            return;
        }
        AttachmentStorage.StoreReceipt receipt = storage.store(command, source(syntheticPdf()));
        delete(storage, binding, new Stored(allocation, command, receipt, receipt.evidence()));
    }

    private static void recoverDeleteCrash(SecureLocalAttachmentStorage storage, StorageBindingRef binding,
                                           String cut, CrashState state) throws Exception {
        AttachmentStorage.DeleteCommand delete = new AttachmentStorage.DeleteCommand(binding, state.identity(), state.evidence(),
                Instant.now().plusSeconds(10));
        if ("DELETE_CONTENT_REMOVED".equals(cut)) {
            require(!storage.confirmAbsent(delete), "content-delete crash was accepted as absence before descriptor cleanup");
        }
        storage.requestDelete(delete);
        require(storage.confirmAbsent(delete), "delete crash artifact was not exactly reclaimed");
    }

    /**
     * Uses the real profile composition with an explicit empty independent report source.  This
     * is intentionally before any storage allocation: a property, a live scanner, or a root
     * alone can never self-attest B24 write/read readiness.
     */
    private static void runReadinessFailClosedCanary(Path root, byte[] binding, StreamingInspectionEngine engine,
                                                      Set<String> passed) throws Exception {
        SecureLocalAttachmentProperties properties = completeProperties(root, binding);
        SecureLocalAttachmentProfileState state = new SecureLocalAttachmentProfileState(properties,
                java.util.Optional::<SecureLocalAttachmentConformanceReportSource.Report>empty, engine, Clock.systemUTC());
        require(!state.policy().enabled() && state.storage() == null, "missing report enabled the profile");
        require(state.readinessEvidence(properties, engine, Instant.now()).isEmpty(),
                "missing report enabled inspection readiness");
        AttachmentStorage unavailable = new SecureLocalAttachmentProfileConfiguration().attachmentStorage(state);
        StorageBindingRef opaqueBinding = new StorageBindingRef(binding);
        expectFailure(() -> unavailable.allocate(opaqueBinding, 1, Instant.now().plusSeconds(10)));
        expectFailure(() -> unavailable.open(null));
        passed.add("S-01"); passed.add("I-13");
    }

    private static SecureLocalAttachmentProperties completeProperties(Path root, byte[] binding) {
        SecureLocalAttachmentProperties properties = new SecureLocalAttachmentProperties();
        properties.setEnabled(true);
        properties.setStorageProfile(SecureLocalAttachmentConformanceContract.profile());
        properties.setSecureLocalRoot(root);
        properties.setBindingBase64(Base64.getUrlEncoder().withoutPadding().encodeToString(binding));
        properties.getInspection().setDetectorProfile("BOUNDED_MAGIC_V1");
        properties.getInspection().setScannerProfile("STREAMING_ENGINE_V1");
        properties.getInspection().setDetectorCatalogRevision("bounded-magic-v1");
        properties.getInspection().setAllowlistRevision("pdf-png-jpeg-v1");
        properties.getInspection().setDefinitionMaxAge(Duration.ofMinutes(5));
        var policy = properties.getPolicy();
        policy.setMaxFileBytes(SecureLocalAttachmentStorage.DEFAULT_MAX_OBJECT_BYTES);
        policy.setMaxReadyBytes(SecureLocalAttachmentStorage.DEFAULT_MAX_OBJECT_BYTES * 2);
        policy.setMaxQuarantineBytes(SecureLocalAttachmentStorage.DEFAULT_MAX_OBJECT_BYTES * 2);
        policy.setMaxPendingIntents(10L); policy.setMaxActiveUploads(2); policy.setMaxActiveInspections(2);
        policy.setMaxActiveDownloads(2); policy.setGlobalReserveBytes(SecureLocalAttachmentStorage.DEFAULT_MAX_OBJECT_BYTES * 4);
        policy.setDeletionControlReserve(1L); policy.setLocalFreeFloorBytes(4L * 1024L); policy.setIntentTtl(Duration.ofSeconds(30));
        policy.setUploadOpenTimeout(Duration.ofSeconds(2)); policy.setUploadIdleTimeout(Duration.ofSeconds(2));
        policy.setUploadTotalTimeout(Duration.ofSeconds(30)); policy.setInspectionTotalTimeout(Duration.ofSeconds(30));
        policy.setDownloadOpenTimeout(Duration.ofSeconds(2)); policy.setDownloadIdleTimeout(Duration.ofSeconds(2));
        policy.setDownloadTotalTimeout(Duration.ofSeconds(30)); policy.setJobLease(Duration.ofSeconds(10));
        policy.setJobHeartbeat(Duration.ofSeconds(2)); policy.setMaxAttempts(2); policy.setQuarantineRetention(Duration.ofSeconds(30));
        policy.setTombstoneRetention(Duration.ofMinutes(1)); policy.setClaimSize(1);
        return properties;
    }

    /** Exercises the production byte-store ceiling with no customer content and no in-memory 64 MiB array. */
    private static void runInputBoundaryCanary(SecureLocalAttachmentStorage storage, StorageBindingRef binding) throws Exception {
        long maximum = SecureLocalAttachmentStorage.DEFAULT_MAX_OBJECT_BYTES;
        Stored oneByte = store(storage, binding, 1, zeroes(1));
        delete(storage, binding, oneByte);

        ZeroesInputStream exactMaximum = new ZeroesInputStream(maximum);
        Stored maximumSize = store(storage, binding, maximum, source(maximum, exactMaximum));
        require(exactMaximum.maxReadRequest() <= 64 * 1024, "secure-local buffer ceiling drifted");
        delete(storage, binding, maximumSize);

        expectFailure(() -> storage.allocate(binding, 0, Instant.now().plusSeconds(10)));
        expectFailure(() -> storage.allocate(binding, maximum + 1, Instant.now().plusSeconds(10)));

        AttachmentStorage.Allocation oversized = storage.allocate(binding, 1, Instant.now().plusSeconds(10));
        AttachmentStorage.StoreCommand oversizedCommand = command(binding, oversized, maximum + 1);
        expectFailedAttempt(storage, binding, oversizedCommand, source(maximum + 1, new ZeroesInputStream(maximum + 1)), 0);

        AttachmentStorage.Allocation shortAttempt = storage.allocate(binding, 2, Instant.now().plusSeconds(10));
        expectFailedAttempt(storage, binding, command(binding, shortAttempt, 2), source(2, new ByteArrayInputStream(new byte[] {1})), 1);
        AttachmentStorage.Allocation longAttempt = storage.allocate(binding, 2, Instant.now().plusSeconds(10));
        expectFailedAttempt(storage, binding, command(binding, longAttempt, 2), source(2, new ByteArrayInputStream(new byte[] {1, 2, 3})), 1);
        AttachmentStorage.Allocation unknownLength = storage.allocate(binding, 1, Instant.now().plusSeconds(10));
        expectFailedAttempt(storage, binding, command(binding, unknownLength, 1), () -> new ByteArrayInputStream(new byte[] {1}), 0);

        AttachmentStorage.Allocation beforeFirstByte = storage.allocate(binding, 1, Instant.now().plusSeconds(10));
        expectFailedAttempt(storage, binding, command(binding, beforeFirstByte, 1), source(1, new FailingBeforeFirstByteInputStream()), 1);

        AttachmentStorage.Allocation slow = storage.allocate(binding, 1, Instant.now().plusSeconds(10));
        AttachmentStorage.StoreCommand slowCommand = new AttachmentStorage.StoreCommand(binding, slow.locator(),
                slow.storageGeneration(), 1, 1, 1, null, Instant.now().plusMillis(5), Duration.ofMillis(5), Duration.ofMillis(5));
        expectFailedAttempt(storage, binding, slowCommand, source(1, new DelayedOneByteInputStream()), 1);

        AttachmentStorage.Allocation aborted = storage.allocate(binding, 2, Instant.now().plusSeconds(10));
        expectFailedAttempt(storage, binding, command(binding, aborted, 2), source(2, new FailingAfterOneByteInputStream()), 1);
    }

    /* A small private carrier keeps opaque locator/generation out of report records and console output. */
    private record Stored(AttachmentStorage.Allocation allocation, AttachmentStorage.StoreCommand command,
                          AttachmentStorage.StoreReceipt receipt, AttachmentContentEvidence evidence) { }

    /** Opaque state exchanged only through the parent/child process pipe during a crash cut. */
    private record CrashState(AttachmentStorage.Allocation allocation, StoredObjectIdentity identity,
                              AttachmentContentEvidence evidence) {
        private static CrashState forStore(AttachmentStorage.Allocation allocation) {
            return new CrashState(allocation, null, null);
        }
        private static CrashState forDelete(AttachmentStorage.StoreReceipt receipt) {
            return new CrashState(null, receipt.identity(), receipt.evidence());
        }
        private String encode() {
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            if (allocation != null) return "S|" + encoder.encodeToString(allocation.locator().copyValue()) + "|"
                    + encoder.encodeToString(allocation.storageGeneration());
            return "D|" + encoder.encodeToString(identity.copyValue()) + "|" + encoder.encodeToString(evidence.sha256())
                    + "|" + evidence.actualBytes() + "|" + evidence.contentVersion();
        }
        private static CrashState parse(byte[] output) {
            String value = new String(output, StandardCharsets.US_ASCII).strip();
            String[] parts = value.split("\\|", -1);
            Base64.Decoder decoder = Base64.getUrlDecoder();
            try {
                if (parts.length == 3 && "S".equals(parts[0])) return forStore(new AttachmentStorage.Allocation(
                        new PlannedObjectLocator(decoder.decode(parts[1])), decoder.decode(parts[2])));
                if (parts.length == 5 && "D".equals(parts[0])) return new CrashState(null,
                        new StoredObjectIdentity(decoder.decode(parts[1])), new AttachmentContentEvidence(
                        Long.parseLong(parts[3]), decoder.decode(parts[2]), Long.parseLong(parts[4])));
            } catch (IllegalArgumentException ignored) {
                // The parent exposes no child output; malformed private state is a generic failed cut.
            }
            throw new IllegalStateException("process crash child state is invalid");
        }
    }

    private static void emit(CrashState state) {
        System.out.println(state.encode());
        System.out.flush();
    }

    private static Stored store(SecureLocalAttachmentStorage storage, StorageBindingRef binding, byte[] bytes) throws Exception {
        AttachmentStorage.Allocation allocation = storage.allocate(binding, bytes.length, Instant.now().plusSeconds(10));
        AttachmentStorage.StoreCommand command = new AttachmentStorage.StoreCommand(binding, allocation.locator(),
                allocation.storageGeneration(), 1, 1, bytes.length, sha256(bytes), Instant.now().plusSeconds(10),
                Duration.ofSeconds(2), Duration.ofSeconds(2));
        AttachmentStorage.StoreReceipt receipt = storage.store(command, source(bytes));
        return new Stored(allocation, command, receipt, evidence(bytes));
    }

    private static Stored store(SecureLocalAttachmentStorage storage, StorageBindingRef binding, long length,
                                AttachmentByteSource source) throws Exception {
        AttachmentStorage.Allocation allocation = storage.allocate(binding, length, Instant.now().plusSeconds(30));
        AttachmentStorage.StoreCommand command = command(binding, allocation, length);
        AttachmentStorage.StoreReceipt receipt = storage.store(command, source);
        return new Stored(allocation, command, receipt, receipt.evidence());
    }

    private static AttachmentStorage.StoreCommand command(StorageBindingRef binding, AttachmentStorage.Allocation allocation,
                                                          long length) {
        return new AttachmentStorage.StoreCommand(binding, allocation.locator(), allocation.storageGeneration(),
                1, 1, length, null, Instant.now().plusSeconds(10), Duration.ofSeconds(2), Duration.ofSeconds(2));
    }

    private static void delete(SecureLocalAttachmentStorage storage, StorageBindingRef binding, Stored stored) throws Exception {
        AttachmentStorage.DeleteCommand delete = new AttachmentStorage.DeleteCommand(binding, stored.receipt().identity(),
                stored.evidence(), Instant.now().plusSeconds(10));
        storage.requestDelete(delete);
        require(storage.confirmAbsent(delete), "boundary canary delete did not converge");
    }

    private static void expectFailedAttempt(SecureLocalAttachmentStorage storage, StorageBindingRef binding,
                                            AttachmentStorage.StoreCommand command, AttachmentByteSource source,
                                            int expectedSourceOpens) throws Exception {
        OpenCountingSource observed = new OpenCountingSource(source);
        expectFailure(() -> storage.store(command, observed));
        require(observed.opens() == expectedSourceOpens, "failed store reopened its source");
        require(storage.activeStoreOperationsForConformance() == 0, "failed store retained an active slot");
        require(storage.abortIncomplete(binding, command.locator(), command.storageGeneration(), command.attempt(),
                Instant.now().plusSeconds(10)) == AttachmentStorage.AbortState.ABSENT, "failed attempt was not released");
        require(storage.activeStoreOperationsForConformance() == 0, "failed cleanup retained an active slot");
    }

    private static void expectFailure(ThrowingOperation operation) throws Exception {
        boolean failed = false;
        try { operation.run(); }
        catch (Exception expected) { failed = true; }
        require(failed, "invalid boundary input was accepted");
    }

    private static void runScannerCanary(ClamdInstreamStreamingInspectionEngine engine,
                                         StreamingInspectionEngine.Evidence expected, Set<String> passed) throws Exception {
        AttachmentContentScanner.ScanResult pdf = engine.scan(new ByteArrayInputStream(syntheticPdf()), evidence(syntheticPdf()));
        AttachmentContentScanner.ScanResult png = engine.scan(new ByteArrayInputStream(syntheticPng()), evidence(syntheticPng()));
        AttachmentContentScanner.ScanResult jpeg = engine.scan(new ByteArrayInputStream(syntheticJpeg()), evidence(syntheticJpeg()));
        byte[] signature = String.join("", EICAR_SEGMENTS).getBytes(StandardCharsets.US_ASCII);
        AttachmentContentScanner.ScanResult eicar = engine.scan(
                new ByteArrayInputStream(signature), evidence(signature));
        require(pdf.verdict() == AttachmentContentScanner.ScanVerdict.NO_THREAT_DETECTED
                && png.verdict() == AttachmentContentScanner.ScanVerdict.NO_THREAT_DETECTED
                && jpeg.verdict() == AttachmentContentScanner.ScanVerdict.NO_THREAT_DETECTED, "benign canary was not clean");
        require(eicar.verdict() == AttachmentContentScanner.ScanVerdict.THREAT_DETECTED, "EICAR was not rejected");
        require(expected.definitionGeneration().equals(pdf.definitionGeneration())
                && expected.definitionBasis() == pdf.definitionBasis(), "scanner generation drift");
        passed.add("I-01"); passed.add("I-02"); passed.add("I-07"); passed.add("I-12");
    }

    private static void writeReport(Path report, Path root, byte[] binding, String policyDigest,
                                    StreamingInspectionEngine.Evidence engine, Map<String, String> options,
                                    Set<String> passed, Set<String> observed, boolean loopback) throws Exception {
        String detectorCatalog = require(options, "--detector-catalog-revision");
        String allowlist = require(options, "--allowlist-revision");
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(5));
        String scenarios = passed.stream().map(value -> "\"" + value + "\"").reduce((a, b) -> a + "," + b).orElse("");
        String checks = observed.stream().map(value -> "\"" + value + "\"").reduce((a, b) -> a + "," + b).orElse("");
        String json = "{\"contractVersion\":\"" + SecureLocalAttachmentConformanceContract.contractVersion()
                // A loopback-only scanner canary is useful backend evidence but never a current
                // production build attestation. Linux Unix-socket mode is intentionally bound to
                // the exact runtime digest, while the verifier still requires all scenarios.
                + "\",\"adapterBuildDigest\":\"" + (loopback ? "CANARY_LOOPBACK_ONLY"
                : SecureLocalAttachmentConformanceContract.currentAdapterBuildDigest())
                + "\",\"engineBuildDigest\":\"" + engine.engineBuildDigest()
                + "\",\"bindingDigest\":\"" + SecureLocalAttachmentConformanceContract.bindingDigest(new StorageBindingRef(binding))
                + "\",\"storageRootDigest\":\"" + SecureLocalAttachmentConformanceContract.storageRootDigest(root)
                + "\",\"policyDigest\":\"" + policyDigest
                + "\",\"detectorProfile\":\"BOUNDED_MAGIC_V1\",\"detectorCatalogRevision\":\"" + detectorCatalog
                + "\",\"scannerProfile\":\"STREAMING_ENGINE_V1\",\"allowlistRevision\":\"" + allowlist
                + "\",\"definitionGeneration\":\"" + engine.definitionGeneration()
                + "\",\"definitionBasis\":\"" + engine.definitionBasis()
                + "\",\"definitionsValidUntil\":\"" + engine.definitionsValidUntil()
                + "\",\"expiresAt\":\"" + expiresAt + "\",\"observedChecks\":[" + checks
                + "],\"passedScenarios\":[" + scenarios + "]}";
        Path parent = report.getParent();
        if (parent == null || !Files.isDirectory(parent)) throw new IllegalArgumentException("report parent is unavailable");
        Path temporary = Files.createTempFile(parent, ".b24-conformance-", ".json");
        try {
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(temporary, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            try { Files.move(temporary, report, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, report, StandardCopyOption.REPLACE_EXISTING); }
            Files.setPosixFilePermissions(report, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } finally { Files.deleteIfExists(temporary); }
    }

    private static SecureLocalAttachmentProperties.Clamd clamd(Map<String, String> options, boolean loopback) {
        SecureLocalAttachmentProperties.Clamd value = new SecureLocalAttachmentProperties.Clamd();
        boolean unix = options.containsKey("--clamd-unix-socket");
        if (loopback == unix) throw new IllegalArgumentException("exactly one clamd transport is required");
        value.setEnabled(true);
        if (loopback) value.setLoopbackTcpPort(Integer.parseInt(require(options, "--clamd-loopback-port")));
        else value.setUnixSocket(absolute(options, "--clamd-unix-socket"));
        value.setChunkBytes(64 * 1024); value.setConnectTimeout(Duration.ofSeconds(3));
        value.setResponseTimeout(Duration.ofSeconds(15)); value.setDefinitionEvidenceTtl(Duration.ofMinutes(5));
        return value;
    }
    private static AttachmentByteSource source(byte[] bytes) {
        return new AttachmentByteSource() {
            @Override public ByteArrayInputStream openStream() { return new ByteArrayInputStream(bytes); }
            @Override public long declaredLength() { return bytes.length; }
        };
    }
    private static AttachmentByteSource source(long declaredLength, InputStream input) {
        return new AttachmentByteSource() {
            @Override public InputStream openStream() { return input; }
            @Override public long declaredLength() { return declaredLength; }
        };
    }
    private static AttachmentByteSource zeroes(long length) { return source(length, new ZeroesInputStream(length)); }
    private static AttachmentContentEvidence evidence(byte[] bytes) { return new AttachmentContentEvidence(bytes.length, sha256(bytes), 1); }
    private static byte[] sha256(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
    private static byte[] syntheticPdf() { return "%PDF-1.4\\n% synthetic B24 canary\\n%%EOF\\n".getBytes(StandardCharsets.US_ASCII); }
    private static byte[] syntheticPng() { return new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}; }
    private static byte[] syntheticJpeg() { return new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9}; }
    @FunctionalInterface private interface ThrowingOperation { void run() throws Exception; }
    private static final class ZeroesInputStream extends InputStream {
        private final AtomicLong maximumReadRequest = new AtomicLong(); private long remaining;
        private ZeroesInputStream(long remaining) { this.remaining = remaining; }
        @Override public int read(byte[] bytes, int offset, int length) {
            maximumReadRequest.accumulateAndGet(length, Math::max);
            if (remaining == 0) return -1;
            int count = (int) Math.min(remaining, length);
            java.util.Arrays.fill(bytes, offset, offset + count, (byte) 0);
            remaining -= count;
            return count;
        }
        @Override public int read() {
            if (remaining == 0) return -1;
            remaining--; return 0;
        }
        private long maxReadRequest() { return maximumReadRequest.get(); }
    }
    private static final class DelayedOneByteInputStream extends InputStream {
        private boolean emitted;
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            if (emitted) return -1;
            try { Thread.sleep(30); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IOException("canary delay interrupted", exception); }
            emitted = true; bytes[offset] = 0; return 1;
        }
        @Override public int read() throws IOException { return read(new byte[1], 0, 1) < 0 ? -1 : 0; }
    }
    private static final class FailingBeforeFirstByteInputStream extends InputStream {
        @Override public int read(byte[] bytes, int offset, int length) throws IOException { throw new IOException("canary pre-byte abort"); }
        @Override public int read() throws IOException { throw new IOException("canary pre-byte abort"); }
    }
    private static final class FailingAfterOneByteInputStream extends InputStream {
        private boolean emitted;
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            if (emitted) throw new IOException("canary client abort");
            emitted = true; bytes[offset] = 0; return 1;
        }
        @Override public int read() throws IOException { return read(new byte[1], 0, 1) < 0 ? -1 : 0; }
    }
    private static final class OpenCountingSource implements AttachmentByteSource {
        private final AttachmentByteSource delegate; private final AtomicInteger opens = new AtomicInteger();
        private OpenCountingSource(AttachmentByteSource delegate) { this.delegate = delegate; }
        @Override public InputStream openStream() throws IOException { opens.incrementAndGet(); return delegate.openStream(); }
        @Override public long declaredLength() { return delegate.declaredLength(); }
        private int opens() { return opens.get(); }
    }
    private static Map<String, String> options(String[] args) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index++) {
            String key = args[index];
            if (!key.startsWith("--") || values.containsKey(key)) throw new IllegalArgumentException("canary arguments are invalid");
            values.put(key, "--allow-synthetic-canary".equals(key) ? "true" : next(args, ++index));
        }
        return Map.copyOf(values);
    }
    private static String next(String[] args, int index) {
        if (index >= args.length || args[index].startsWith("--")) throw new IllegalArgumentException("canary argument is missing");
        return args[index];
    }
    private static String require(Map<String, String> options, String key) {
        String value = options.get(key); if (value == null || value.isBlank()) throw new IllegalArgumentException("canary argument is missing"); return value;
    }
    private static Path absolute(Map<String, String> options, String key) { return Path.of(require(options, key)).toAbsolutePath().normalize(); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
