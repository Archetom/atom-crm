package io.github.archetom.crm.infra.external.attachment;

import io.github.archetom.crm.application.port.out.attachment.AttachmentByteSource;
import io.github.archetom.crm.application.port.out.attachment.AttachmentRange;
import io.github.archetom.crm.application.port.out.attachment.AttachmentReadHandle;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.PlannedObjectLocator;
import io.github.archetom.crm.application.port.out.attachment.StorageBindingRef;
import io.github.archetom.crm.application.port.out.attachment.StoredObjectIdentity;
import io.github.archetom.crm.domain.model.AttachmentContentEvidence;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Secure-directory-stream local adapter. Each opaque binding is cryptographically namespaced
 * below the single pre-provisioned root and is also embedded in every stored identity. A caller
 * cannot reuse an identity with a different binding for probe, read or delete.
 */
public final class SecureLocalAttachmentStorage implements AttachmentStorage, AutoCloseable {
    private static final int BUFFER = 64 * 1024;
    /** The first secure-local profile never accepts a larger object, even if a caller bypasses admission. */
    static final long DEFAULT_MAX_OBJECT_BYTES = 64L * 1024L * 1024L;
    /**
     * LOCAL_SECURE_NIO_V1 is single-node.  Stripes cover every adapter instance in this JVM so a
     * duplicate PUT cannot observe a descriptor until the winning store has finished its durable
     * directory commit (or its exact rollback).  Shared-mount/multi-process deployments remain
     * NOT_READY until they provide the separate cross-replica fencing evidence required by B24-S.
     */
    private static final ReentrantLock[] STORE_COMMIT_FENCES = commitFences();
    private static final SecureRandom RANDOM = new SecureRandom();
    /*
     * This hook has no configuration or production call path.  The separate B24 child-process
     * canary installs it only to halt its own JVM immediately after a named durable boundary;
     * normal adapters retain the no-op implementation.
     */
    private static volatile ConformanceCheckpointHook conformanceCheckpointHook = checkpoint -> { };
    private static final ScheduledExecutorService STREAM_DEADLINES = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = Thread.ofPlatform().daemon().name("attachment-stream-deadline").unstarted(task);
        return thread;
    });
    private static final ExecutorService OPEN_OPERATIONS = Executors.newVirtualThreadPerTaskExecutor();
    private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY_FILE =
            PosixFilePermissions.asFileAttribute(Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
    private final Path root;
    private final RootEvidence rootEvidence;
    private final long maxObjectBytes;
    /** Deployment-owned capacity floor; a free-space sample is a fail-closed admission hint only. */
    private final long minimumFreeBytes;
    /** Per-adapter live store count; package scope is reserved for the independent B24 runner. */
    private final java.util.concurrent.atomic.AtomicInteger activeStoreOperations = new java.util.concurrent.atomic.AtomicInteger();
    /** Opened once for the attested root identity; never re-opened by pathname for an fsync. */
    private final FileChannel rootDirectory;

    public SecureLocalAttachmentStorage(Path root) throws IOException {
        this(root, DEFAULT_MAX_OBJECT_BYTES, 0L);
    }

    /**
     * Repeat the runtime object ceiling at the byte-store boundary so a direct port caller cannot
     * stream an oversized object into the secure-local root.
     */
    public SecureLocalAttachmentStorage(Path root, long maxObjectBytes) throws IOException {
        this(root, maxObjectBytes, 0L);
    }

    /**
     * Applies the secure-local deployment reserve in addition to the per-object ceiling.  The
     * filesystem's free-space value cannot promise a future write under concurrent processes,
     * so the adapter samples it before admission and still treats any subsequent write failure
     * as an exact rollback/reconciliation outcome.
     */
    public SecureLocalAttachmentStorage(Path root, long maxObjectBytes, long minimumFreeBytes) throws IOException {
        if (maxObjectBytes < 1 || maxObjectBytes > DEFAULT_MAX_OBJECT_BYTES) {
            throw new IllegalArgumentException("secure local object limit is invalid");
        }
        if (minimumFreeBytes < 0) throw new IllegalArgumentException("secure local free floor is invalid");
        this.root = resolveRoot(root);
        this.rootEvidence = RootEvidence.capture(this.root);
        this.maxObjectBytes = maxObjectBytes;
        this.minimumFreeBytes = minimumFreeBytes;
        this.rootDirectory = openRootDirectory(this.root);
        try (var stream = Files.newDirectoryStream(this.root)) {
            if (!(stream instanceof SecureDirectoryStream<?>)) throw new IOException("secure directory streams are unavailable");
        } catch (IOException | RuntimeException exception) {
            closeRootDirectory();
            throw exception;
        }
        // A local profile cannot claim crash-safe publication/removal unless the mounted
        // filesystem exposes a directory fsync operation.  Fail closed during composition so
        // the disabled lane remains available on platforms which cannot provide it.
        try {
            forceAttestedRootDirectory();
            if (minimumFreeBytes > 0) requireFreeCapacity(1L);
        } catch (IOException | RuntimeException exception) {
            closeRootDirectory();
            throw exception;
        }
    }

    /** Stable evidence supplied to the independent conformance report and rechecked per operation. */
    public static String rootAttestationDigest(Path root) throws IOException {
        return RootEvidence.capture(resolveRoot(root)).digest();
    }

    @Override public Allocation allocate(StorageBindingRef binding, long expectedBytes, Instant deadline) {
        if (binding == null || expectedBytes <= 0 || expectedBytes > maxObjectBytes || deadline == null) {
            throw new IllegalArgumentException("allocation is invalid");
        }
        requireBeforeDeadline(deadline);
        try { requireCurrentRoot(); }
        catch (IOException exception) { throw new IllegalStateException("secure local root is not current", exception); }
        try { requireFreeCapacity(expectedBytes); }
        catch (IOException exception) { throw new IllegalStateException("secure local capacity is not ready", exception); }
        bindingFingerprint(binding); // reject malformed opaque binding before allocating its namespace.
        return new Allocation(new PlannedObjectLocator(randomName().getBytes(java.nio.charset.StandardCharsets.US_ASCII)), random(32));
    }

    @Override public StoreReceipt store(StoreCommand command, AttachmentByteSource source) throws Exception {
        if (command == null || source == null || command.binding() == null || command.locator() == null
                || command.storageGeneration() == null || command.storageGeneration().length != 32
                || command.contentVersion() < 1 || command.attempt() < 1 || command.expectedBytes() < 1
                || command.deadline() == null)
            throw new IllegalArgumentException("store command is invalid");
        if (command.expectedBytes() > maxObjectBytes || source.declaredLength() != command.expectedBytes()) {
            throw new StoreConflictException("attachment stream length is not transport-proven");
        }
        activeStoreOperations.incrementAndGet();
        try {
            return storeCurrent(command, source);
        } finally {
            activeStoreOperations.decrementAndGet();
        }
    }

    /** An independent runner may assert resource convergence without exposing this as application state. */
    int activeStoreOperationsForConformance() { return activeStoreOperations.get(); }

    /** Package-private process-crash seam reserved for the isolated B24 synthetic canary. */
    static AutoCloseable installConformanceCheckpointHook(ConformanceCheckpointHook hook) {
        if (hook == null) throw new IllegalArgumentException("conformance hook is required");
        ConformanceCheckpointHook previous = conformanceCheckpointHook;
        conformanceCheckpointHook = hook;
        return () -> conformanceCheckpointHook = previous;
    }

    private StoreReceipt storeCurrent(StoreCommand command, AttachmentByteSource source) throws Exception {
        requireBeforeDeadline(command.deadline());
        byte[] binding = bindingFingerprint(command.binding());
        String locator = locator(command.locator());
        String name = physicalName(binding, locator);
        byte[] generation = command.storageGeneration();
        try (StoreCommitFence commitFence = acquireStoreCommitFence(name, command.deadline());
             InputStream input = openWithin(source, command.deadline(), command.openTimeout());
             InputStream bounded = new TimedInputStream(input, command.deadline(), command.idleTimeout());
             SecureDirectoryStream<Path> directory = directory()) {
            boolean contentCreated = false;
            try {
                // Do not reject an exact duplicate retry merely because capacity has fallen
                // after the winner committed. A never-used namespace must meet the current
                // deployment floor before it can consume bytes.
                if (absent(directory, name) && absent(directory, descriptorName(name))) {
                    requireFreeCapacity(command.expectedBytes());
                }
                try (SeekableByteChannel content = directory.newByteChannel(Path.of(name), writeOptions(), OWNER_ONLY_FILE)) {
                    contentCreated = true;
                    Evidence written = copyEvidence(bounded, content, command.expectedBytes(), command.deadline());
                    checkpoint("STORE_CONTENT_FORCED");
                    if (command.expectedSha256() != null && !MessageDigest.isEqual(command.expectedSha256(), written.sha())) {
                        throw new StoreConflictException("attachment checksum does not match intent");
                    }
                    Descriptor descriptor = new Descriptor(command.contentVersion(), command.attempt(), written.length(),
                            written.sha(), generation);
                    writeDescriptor(directory, name, descriptor);
                    checkpoint("STORE_DESCRIPTOR_FORCED");
                    forceDirectory(directory);
                    checkpoint("STORE_DIRECTORY_FORCED");
                    return receipt(binding, locator, descriptor);
                }
            } catch (FileAlreadyExistsException exception) {
                // The input has not been consumed: CREATE_NEW failed before a content channel
                // existed. Compare it exactly once against the committed descriptor instead of
                // reopening a one-shot servlet stream or aborting the winner.
                return existingReceipt(command, bounded, binding, locator, name, directory);
            } catch (Exception exception) {
                if (contentCreated) {
                    try {
                        deleteOwnedIfExists(directory, name);
                        deleteOwnedIfExists(directory, descriptorName(name));
                        forceDirectory(directory);
                    } catch (Exception cleanup) {
                        exception.addSuppressed(cleanup);
                    }
                }
                throw exception;
            }
        }
    }

    @Override public ProbeResult probe(ProbeCommand command) throws Exception {
        if (command == null || command.binding() == null || command.locator() == null
                || command.storageGeneration() == null || command.storageGeneration().length != 32
                || command.deadline() == null) throw new IllegalArgumentException("probe command is invalid");
        requireBeforeDeadline(command.deadline());
        byte[] fingerprint = bindingFingerprint(command.binding());
        String logicalName = locator(command.locator());
        String name = physicalName(fingerprint, logicalName);
        try (StoreCommitFence commitFence = acquireStoreCommitFence(name, command.deadline());
             SecureDirectoryStream<Path> directory = directory()) {
            Descriptor descriptor;
            try { descriptor = descriptor(directory, name); }
            catch (NoSuchFileException exception) {
                if (!absent(directory, name)) return new ProbeResult(ProbeState.MISMATCH, null, null);
                return new ProbeResult(ProbeState.ABSENT, null, null);
            }
            StoredObjectIdentity actual = new StoredObjectIdentity(identity(fingerprint, logicalName, descriptor.generation()));
            if (!Arrays.equals(command.storageGeneration(), descriptor.generation())) {
                return new ProbeResult(ProbeState.MISMATCH, actual, null);
            }
            if (command.expectedIdentity() != null) {
                Identity expected = identity(command.expectedIdentity());
                if (!Arrays.equals(expected.binding(), fingerprint) || !expected.name().equals(logicalName)
                        || !Arrays.equals(expected.generation(), descriptor.generation())) {
                    return new ProbeResult(ProbeState.MISMATCH, actual, null);
                }
            }
            requireRegularOwnedFile(directory, name);
            try (SeekableByteChannel content = directory.newByteChannel(Path.of(name), readOptions())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); long count = copyDigest(content, digest, command.deadline());
            if (count != descriptor.length() || !MessageDigest.isEqual(digest.digest(), descriptor.sha()))
                return new ProbeResult(ProbeState.MISMATCH, actual, null);
            }
            return new ProbeResult(ProbeState.PRESENT, actual,
                    new AttachmentContentEvidence(descriptor.length(), descriptor.sha(), descriptor.contentVersion()));
        }
    }

    @Override public AttachmentReadHandle open(OpenCommand command) throws Exception {
        if (command == null || command.binding() == null || command.identity() == null || command.evidence() == null)
            throw new IllegalArgumentException("open command is invalid");
        requireBeforeDeadline(command.deadline());
        return callWithin("attachment read open", command.deadline(), command.openTimeout(),
                () -> openLocal(command), SecureLocalAttachmentStorage::closeReadHandle);
    }

    private AttachmentReadHandle openLocal(OpenCommand command) throws Exception {
        Identity identity = requireIdentity(command.binding(), command.identity());
        String name = physicalName(identity.binding(), identity.name());
        try (StoreCommitFence commitFence = acquireStoreCommitFence(name, command.deadline())) {
            SecureDirectoryStream<Path> directory = directory();
            SeekableByteChannel channel = null;
            boolean transferred = false;
            try {
            Descriptor descriptor = descriptor(directory, name);
            if (!Arrays.equals(identity.generation(), descriptor.generation()) || descriptor.length() != command.evidence().actualBytes()
                    || descriptor.contentVersion() != command.evidence().contentVersion()
                    || !MessageDigest.isEqual(descriptor.sha(), command.evidence().sha256())) throw new IOException("stored identity mismatch");
            AttachmentRange range = command.range() == null ? new AttachmentRange(0, descriptor.length(), descriptor.length()) : command.range();
            requireRegularOwnedFile(directory, name);
            channel = directory.newByteChannel(Path.of(name), readOptions());
            // Validate the actual bytes on the exact opened channel before exposing any stream.
            // Descriptor/evidence equality alone cannot detect a same-length replacement.
            verifyOpenedContent(directory, name, descriptor, channel, command.deadline());
            channel.position(range.offset());
                LocalReadHandle handle = new LocalReadHandle(directory, channel, name, descriptor, range.length(),
                        command.deadline(), command.idleTimeout());
                transferred = true;
                return handle;
            } finally {
                if (!transferred) {
                    if (channel != null) try { channel.close(); } catch (IOException ignored) { }
                    try { directory.close(); } catch (IOException ignored) { }
                }
            }
        }
    }

    @Override public void requestDelete(DeleteCommand command) throws Exception {
        if (command == null || command.binding() == null || command.identity() == null || command.evidence() == null)
            throw new IllegalArgumentException("delete command is invalid");
        requireBeforeDeadline(command.deadline());
        Identity identity = requireIdentity(command.binding(), command.identity());
        String name = physicalName(identity.binding(), identity.name());
        try (StoreCommitFence commitFence = acquireStoreCommitFence(name, command.deadline());
             SecureDirectoryStream<Path> directory = directory()) {
            try {
                Descriptor descriptor = descriptor(directory, name);
                if (!Arrays.equals(identity.generation(), descriptor.generation())
                        || descriptor.contentVersion() != command.evidence().contentVersion()
                        || descriptor.length() != command.evidence().actualBytes()
                        || !MessageDigest.isEqual(descriptor.sha(), command.evidence().sha256())) {
                    throw new IOException("delete identity mismatch");
                }
            } catch (NoSuchFileException ignored) {
                // A crash can leave a content-only partial object. The exact opaque names are
                // never reused, so a verified ordinary file can still be reclaimed below.
            }
            deleteOwnedIfExists(directory, name);
            checkpoint("DELETE_CONTENT_REMOVED");
            deleteOwnedIfExists(directory, descriptorName(name));
            checkpoint("DELETE_DESCRIPTOR_REMOVED");
            if (!durablyAbsent(directory, name)) {
                throw new IOException("attachment delete did not reach durable absence");
            }
        }
    }

    @Override public boolean confirmAbsent(DeleteCommand command) throws Exception {
        if (command == null || command.binding() == null || command.identity() == null) return false;
        requireBeforeDeadline(command.deadline());
        Identity identity = requireIdentity(command.binding(), command.identity());
        String name = physicalName(identity.binding(), identity.name());
        try (StoreCommitFence commitFence = acquireStoreCommitFence(name, command.deadline());
             SecureDirectoryStream<Path> directory = directory()) {
            return durablyAbsent(directory, name);
        }
    }

    @Override public AbortState abortIncomplete(StorageBindingRef binding, PlannedObjectLocator locator, byte[] storageGeneration,
                                                long attempt, Instant deadline) throws Exception {
        if (storageGeneration == null || storageGeneration.length != 32 || attempt < 1 || deadline == null) {
            throw new IllegalArgumentException("attachment attempt is invalid");
        }
        requireBeforeDeadline(deadline);
        byte[] fingerprint = bindingFingerprint(binding);
        String name = physicalName(fingerprint, locator(locator));
        try (StoreCommitFence commitFence = acquireStoreCommitFence(name, deadline);
             SecureDirectoryStream<Path> directory = directory()) {
            try {
                Descriptor descriptor = descriptor(directory, name);
                if (descriptor.attempt() != attempt || !Arrays.equals(descriptor.generation(), storageGeneration)) {
                    return AbortState.MISMATCH;
                }
            } catch (NoSuchFileException ignored) {
                // Content-only partial writes have no descriptor to fence; the allocator never
                // reuses locators, so they are reclaimable only through this exact namespace.
            }
            deleteOwnedIfExists(directory, name);
            deleteOwnedIfExists(directory, descriptorName(name));
            return durablyAbsent(directory, name) ? AbortState.ABSENT : AbortState.UNKNOWN;
        }
    }

    private StoreReceipt existingReceipt(StoreCommand command, InputStream input, byte[] binding,
                                        String locator, String name, SecureDirectoryStream<Path> directory) throws Exception {
        // The shared store commit fence is held until the winning descriptor directory force has
        // succeeded (or the winner has removed both entries). A stale crash artifact can still
        // lack its descriptor, hence the bounded wait remains necessary, but a live loser never
        // receives a receipt from the winner's pre-durability window.
        Descriptor descriptor = awaitCommittedDescriptor(directory, name, command.deadline());
        if (descriptor.contentVersion() != command.contentVersion() || descriptor.attempt() != command.attempt()
                || !Arrays.equals(descriptor.generation(), command.storageGeneration())
                || descriptor.length() != command.expectedBytes()
                || command.expectedSha256() != null && !MessageDigest.isEqual(command.expectedSha256(), descriptor.sha())) {
            throw new StoreConflictException("attachment upload intent was consumed by different evidence");
        }
        Evidence evidence = copyEvidence(input, null, command.expectedBytes(), command.deadline());
        if (evidence.length() != descriptor.length() || !MessageDigest.isEqual(evidence.sha(), descriptor.sha())) {
            throw new StoreConflictException("attachment upload bytes differ from stored evidence");
        }
        return receipt(binding, locator, descriptor);
    }

    private Descriptor awaitCommittedDescriptor(SecureDirectoryStream<Path> directory, String name,
                                                Instant deadline) throws Exception {
        IOException transientFailure = null;
        while (Instant.now().isBefore(deadline)) {
            try { return descriptor(directory, name); }
            catch (NoSuchFileException exception) { transientFailure = exception; }
            catch (IOException exception) {
                if (!"descriptor is incomplete".equals(exception.getMessage())) throw exception;
                transientFailure = exception;
            }
            try { Thread.sleep(2L); }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("attachment upload wait interrupted", exception);
            }
        }
        throw new IOException("attachment upload descriptor was not committed", transientFailure);
    }

    private void writeDescriptor(SecureDirectoryStream<Path> directory, String name, Descriptor descriptor) throws Exception {
        try (SeekableByteChannel channel = directory.newByteChannel(Path.of(descriptorName(name)), writeOptions(), OWNER_ONLY_FILE)) {
            ByteBuffer bytes = ByteBuffer.wrap(descriptor.bytes());
            while (bytes.hasRemaining()) channel.write(bytes);
            force(channel);
        } catch (FileAlreadyExistsException exception) {
            // The caller removes only its own just-created content through this same directory.
            throw new StoreConflictException("attachment upload intent was already committed");
        }
    }

    private StoreReceipt receipt(byte[] binding, String locator, Descriptor descriptor) {
        return new StoreReceipt(new StoredObjectIdentity(identity(binding, locator, descriptor.generation())),
                new AttachmentContentEvidence(descriptor.length(), descriptor.sha(), descriptor.contentVersion()));
    }

    private static void deleteIfExists(SecureDirectoryStream<Path> directory, String name) throws IOException {
        try { directory.deleteFile(Path.of(name)); } catch (NoSuchFileException ignored) { }
    }
    private void deleteOwnedIfExists(SecureDirectoryStream<Path> directory, String name) throws IOException {
        try { requireRegularOwnedFile(directory, name); }
        catch (NoSuchFileException ignored) { return; }
        deleteIfExists(directory, name);
    }
    private boolean absent(SecureDirectoryStream<Path> directory, String name) throws IOException {
        try {
            requireRegularOwnedFile(directory, name);
            try (var ignored = directory.newByteChannel(Path.of(name), readOptions())) { return false; }
        } catch (NoSuchFileException expected) { return true; }
    }
    /**
     * Directory entries are the commit record for both descriptor publication and deletion.
     * File force alone does not make either namespace mutation survive a power loss.  The root
     * is owner-only and attested before/after the fsync, while entry operations remain relative
     * to the already-proven SecureDirectoryStream.
     */
    private boolean durablyAbsent(SecureDirectoryStream<Path> directory, String name) throws IOException {
        if (!absent(directory, name) || !absent(directory, descriptorName(name))) return false;
        forceDirectory(directory);
        return absent(directory, name) && absent(directory, descriptorName(name));
    }
    @SuppressWarnings("unchecked") private SecureDirectoryStream<Path> directory() throws IOException {
        requireCurrentRoot();
        var stream = Files.newDirectoryStream(root);
        if (!(stream instanceof SecureDirectoryStream<?> secure)) { stream.close(); throw new IOException("secure directory streams are unavailable"); }
        SecureDirectoryStream<Path> directory = (SecureDirectoryStream<Path>) secure;
        try {
            // The path may be rebound between its pre-open verification and newDirectoryStream.
            // Bind the proof to the opened directory itself, then re-check the named root's
            // fileKey/device evidence before returning a stream for relative operations.
            if (!rootEvidence.matchesOpened(directory)) throw new IOException("secure local root evidence changed");
            requireCurrentRoot();
            return directory;
        } catch (Exception exception) {
            try { directory.close(); } catch (IOException ignored) { }
            if (exception instanceof IOException io) throw io;
            throw new IOException("secure local root verification failed", exception);
        }
    }

    private static Path resolveRoot(Path candidate) throws IOException {
        if (candidate == null) throw new IllegalArgumentException("secure local root is required");
        Path resolved = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!resolved.isAbsolute() || Files.isSymbolicLink(resolved)
                || !Files.isDirectory(resolved, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("secure local root is invalid");
        }
        return resolved;
    }

    private void requireCurrentRoot() throws IOException {
        Path resolved = resolveRoot(root);
        if (!resolved.equals(root) || !rootEvidence.equals(RootEvidence.capture(resolved))) {
            throw new IOException("secure local root evidence changed");
        }
    }

    /**
     * Forces the directory descriptor captured at construction, after proving that the secure
     * stream which performed the namespace change is the same directory identity.  Re-opening
     * {@code root} here would make an ABA rebind able to fsync the wrong directory.
     */
    private void forceDirectory(SecureDirectoryStream<Path> directory) throws IOException {
        if (directory == null || !rootEvidence.matchesOpened(directory)) {
            throw new IOException("secure local directory evidence changed before durability force");
        }
        forceAttestedRootDirectory();
        if (!rootEvidence.matchesOpened(directory)) {
            throw new IOException("secure local directory evidence changed after durability force");
        }
    }

    /** Fails closed when the platform/filesystem cannot fsync the attested directory descriptor. */
    private void forceAttestedRootDirectory() throws IOException {
        requireCurrentRoot();
        try {
            rootDirectory.force(true);
        } catch (UnsupportedOperationException exception) {
            throw new IOException("secure local directory durability is unavailable", exception);
        }
        requireCurrentRoot();
    }

    private static FileChannel openRootDirectory(Path root) throws IOException {
        try {
            return FileChannel.open(root, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        } catch (UnsupportedOperationException exception) {
            throw new IOException("secure local directory durability is unavailable", exception);
        }
    }
    private void closeRootDirectory() {
        try { rootDirectory.close(); } catch (IOException ignored) { }
    }
    @Override public void close() { closeRootDirectory(); }

    private record RootEvidence(String canonicalPath, String owner, String permissions,
                                String fileKey, String device, String fileKeyDevice) {
        private static RootEvidence capture(Path root) throws IOException {
            BasicFileAttributes basic = Files.readAttributes(root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            PosixFileAttributes posix = Files.readAttributes(root, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (basic.fileKey() == null) throw new IOException("secure local root file key is unavailable");
            String fileKey = basic.fileKey().toString();
            String fileKeyDevice = deviceInFileKey(fileKey);
            if (fileKeyDevice == null) throw new IOException("secure local root file-key device is unavailable");
            Object device = Files.getAttribute(root, "unix:dev", LinkOption.NOFOLLOW_LINKS);
            if (device == null) throw new IOException("secure local root device is unavailable");
            if (!Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE).equals(posix.permissions())) {
                throw new IOException("secure local root must be owner-only 0700");
            }
            return new RootEvidence(root.toString(), posix.owner().getName(),
                    PosixFilePermissions.toString(posix.permissions()), fileKey, device.toString(), fileKeyDevice);
        }
        private String digest() {
            try {
                String value = String.join("|", canonicalPath, owner, permissions, fileKey, device, fileKeyDevice);
                return Base64.getUrlEncoder().withoutPadding().encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            } catch (Exception exception) {
                throw new IllegalStateException("secure local root digest is unavailable", exception);
            }
        }
        private boolean matchesOpened(SecureDirectoryStream<Path> directory) throws IOException {
            BasicFileAttributes basic = directory.getFileAttributeView(BasicFileAttributeView.class).readAttributes();
            PosixFileAttributes posix = directory.getFileAttributeView(PosixFileAttributeView.class).readAttributes();
            return basic.isDirectory() && basic.fileKey() != null
                    && owner.equals(posix.owner().getName())
                    && permissions.equals(PosixFilePermissions.toString(posix.permissions()))
                    && fileKey.equals(basic.fileKey().toString());
        }
    }
    private Descriptor descriptor(SecureDirectoryStream<Path> directory, String name) throws IOException {
        requireRegularOwnedFile(directory, descriptorName(name));
        try (SeekableByteChannel channel = directory.newByteChannel(Path.of(descriptorName(name)), readOptions())) {
            ByteBuffer bytes = ByteBuffer.allocate(88); while (bytes.hasRemaining() && channel.read(bytes) >= 0) { }
            if (bytes.hasRemaining()) throw new IOException("descriptor is incomplete"); bytes.flip(); return Descriptor.parse(bytes);
        }
    }
    private void requireRegularOwnedFile(SecureDirectoryStream<Path> directory, String name) throws IOException {
        Path entry = Path.of(name);
        BasicFileAttributes basic = directory.getFileAttributeView(entry, BasicFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS).readAttributes();
        PosixFileAttributes posix = directory.getFileAttributeView(entry, PosixFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS).readAttributes();
        String entryDevice = basic.fileKey() == null ? null : deviceInFileKey(basic.fileKey().toString());
        if (!basic.isRegularFile() || basic.isSymbolicLink() || entryDevice == null
                || !rootEvidence.fileKeyDevice().equals(entryDevice)
                || !rootEvidence.owner().equals(posix.owner().getName())
                || !Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE).equals(posix.permissions())) {
            throw new IOException("secure local attachment entry evidence changed");
        }
    }
    private static String deviceInFileKey(String fileKey) {
        if (fileKey == null) return null;
        Matcher matcher = Pattern.compile("(?:^|[,(\\s])dev=([^,\\s)]+)").matcher(fileKey);
        return matcher.find() ? matcher.group(1) : null;
    }
    private static Evidence copyEvidence(InputStream input, SeekableByteChannel output, long expected,
                                         Instant deadline) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); long written = 0; byte[] buffer = new byte[BUFFER];
        for (int read; (read = input.read(buffer)) >= 0;) {
            requireBeforeDeadline(deadline);
            if (read == 0) continue;
            written = Math.addExact(written, read);
            if (written > expected) throw new StoreConflictException("attachment stream exceeded expected length");
            digest.update(buffer, 0, read);
            if (output != null) { ByteBuffer bytes = ByteBuffer.wrap(buffer, 0, read); while (bytes.hasRemaining()) output.write(bytes); }
        }
        if (output != null) force(output);
        if (written != expected) throw new StoreConflictException("attachment stream length does not match intent");
        return new Evidence(written, digest.digest());
    }
    private static long copyDigest(SeekableByteChannel channel, MessageDigest digest, Instant deadline) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER); long count = 0;
        for (int read; (read = channel.read(buffer)) >= 0;) { requireBeforeDeadline(deadline); if (read == 0) continue; count = Math.addExact(count, read); buffer.flip(); digest.update(buffer); buffer.clear(); }
        return count;
    }
    /** Verifies descriptor and full-object evidence using the same content handle served to the caller. */
    private void verifyOpenedContent(SecureDirectoryStream<Path> directory, String name, Descriptor expected,
                                     SeekableByteChannel content, Instant deadline) throws Exception {
        if (!sameDescriptor(expected, descriptor(directory, name))) {
            throw new IOException("stored descriptor changed during read");
        }
        requireRegularOwnedFile(directory, name);
        content.position(0);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long count = copyDigest(content, digest, deadline);
        if (count != expected.length() || !MessageDigest.isEqual(digest.digest(), expected.sha())) {
            throw new IOException("stored content evidence mismatch");
        }
        if (!sameDescriptor(expected, descriptor(directory, name))) {
            throw new IOException("stored descriptor changed after read");
        }
        requireRegularOwnedFile(directory, name);
    }
    private static boolean sameDescriptor(Descriptor left, Descriptor right) {
        return left != null && right != null && left.contentVersion() == right.contentVersion()
                && left.attempt() == right.attempt() && left.length() == right.length()
                && MessageDigest.isEqual(left.sha(), right.sha())
                && MessageDigest.isEqual(left.generation(), right.generation());
    }
    private static Set<OpenOption> writeOptions() { return Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS); }
    private static Set<OpenOption> readOptions() { return Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS); }
    private static void force(SeekableByteChannel channel) throws IOException { if (channel instanceof FileChannel file) file.force(true); else throw new IOException("force is unavailable"); }
    private static void checkpoint(String checkpoint) { conformanceCheckpointHook.reached(checkpoint); }
    private void requireFreeCapacity(long additionalBytes) throws IOException {
        if (additionalBytes < 1) throw new IOException("secure local capacity request is invalid");
        requireCurrentRoot();
        long usable = Files.getFileStore(root).getUsableSpace();
        if (usable < minimumFreeBytes || additionalBytes > usable - minimumFreeBytes) {
            throw new IOException("secure local capacity floor is unavailable");
        }
    }
    private static void requireBeforeDeadline(Instant deadline) {
        if (deadline == null || !Instant.now().isBefore(deadline)) {
            throw new IllegalStateException("attachment operation deadline expired");
        }
    }
    private static InputStream openWithin(AttachmentByteSource source, Instant deadline, Duration openTimeout) throws Exception {
        return callWithin("attachment source open", deadline, openTimeout, source::openStream,
                SecureLocalAttachmentStorage::closeQuietly);
    }
    /**
     * Runs a potentially blocking open in an interruptible virtual thread. Cancellation cannot
     * force a broken filesystem to honour interruption, but it promptly returns control to the
     * caller and closes a late-created handle instead of pinning the worker scheduler.
     */
    private static <T> T callWithin(String operation, Instant deadline, Duration configured,
                                    CheckedSupplier<T> supplier, Consumer<T> lateClose) throws Exception {
        long waitNanos = allowedNanos(deadline, configured);
        AtomicBoolean abandoned = new AtomicBoolean();
        AtomicReference<T> value = new AtomicReference<>();
        Future<T> future = OPEN_OPERATIONS.submit(() -> {
            T result = supplier.get();
            value.set(result);
            if (abandoned.get()) {
                closeLate(lateClose, value.getAndSet(null));
                throw new IOException(operation + " timed out");
            }
            return result;
        });
        try {
            T result = future.get(waitNanos, TimeUnit.NANOSECONDS);
            value.compareAndSet(result, null);
            return result;
        } catch (java.util.concurrent.TimeoutException exception) {
            abandoned.set(true);
            closeLate(lateClose, value.getAndSet(null));
            future.cancel(true);
            throw new IOException(operation + " timed out", exception);
        } catch (InterruptedException exception) {
            abandoned.set(true);
            closeLate(lateClose, value.getAndSet(null));
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException(operation + " interrupted", exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof Exception valueException) throw valueException;
            throw new IOException(operation + " failed", exception.getCause());
        }
    }
    private static <T> void closeLate(Consumer<T> closer, T value) {
        if (value == null) return;
        closer.accept(value);
    }
    @FunctionalInterface private interface CheckedSupplier<T> { T get() throws Exception; }
    private static long allowedNanos(Instant deadline, Duration configured) {
        requireBeforeDeadline(deadline);
        long deadlineNanos;
        try { deadlineNanos = Duration.between(Instant.now(), deadline).toNanos(); }
        catch (ArithmeticException exception) { deadlineNanos = Long.MAX_VALUE; }
        long configuredNanos = configured == null ? Long.MAX_VALUE : configured.toNanos();
        if (configuredNanos <= 0) throw new IllegalArgumentException("attachment timeout is invalid");
        return Math.max(1, Math.min(deadlineNanos, configuredNanos));
    }
    private static void closeQuietly(InputStream input) {
        if (input == null) return;
        try { input.close(); } catch (IOException ignored) { }
    }
    private static void closeReadHandle(AttachmentReadHandle handle) {
        if (handle == null) return;
        try { handle.close(); } catch (IOException ignored) { }
    }
    private static ReentrantLock[] commitFences() {
        ReentrantLock[] fences = new ReentrantLock[257];
        for (int index = 0; index < fences.length; index++) fences[index] = new ReentrantLock();
        return fences;
    }
    /** Holds a winner/duplicate fence until descriptor publication is durable or rolled back. */
    private StoreCommitFence acquireStoreCommitFence(String physicalName, Instant deadline) throws IOException {
        ReentrantLock fence = STORE_COMMIT_FENCES[Math.floorMod(physicalName.hashCode(), STORE_COMMIT_FENCES.length)];
        try {
            if (!fence.tryLock(allowedNanos(deadline, null), TimeUnit.NANOSECONDS)) {
                throw new IOException("attachment store commit fence timed out");
            }
            return new StoreCommitFence(fence);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("attachment store commit fence interrupted", exception);
        }
    }
    private static String locator(PlannedObjectLocator locator) { return checkedLocator(new String(locator.copyValue(), java.nio.charset.StandardCharsets.US_ASCII)); }
    private static String checkedLocator(String value) { if (!value.matches("[A-Za-z0-9_-]{43}")) throw new IllegalArgumentException("opaque local locator is invalid"); return value; }
    private static String descriptorName(String name) { return name + ".d"; }
    private static String randomName() { return Base64.getUrlEncoder().withoutPadding().encodeToString(random(32)); }
    private static byte[] random(int size) { byte[] value = new byte[size]; RANDOM.nextBytes(value); return value; }
    private static byte[] bindingFingerprint(StorageBindingRef binding) {
        if (binding == null || binding.copyValue().length == 0) throw new IllegalArgumentException("storage binding is invalid");
        try { return MessageDigest.getInstance("SHA-256").digest(binding.copyValue()); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private static String physicalName(byte[] binding, String locator) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(binding) + "_" + checkedLocator(locator);
    }
    private static byte[] identity(byte[] binding, String name, byte[] generation) {
        ByteBuffer out = ByteBuffer.allocate(32 + 43 + 32); out.put(binding).put(name.getBytes(java.nio.charset.StandardCharsets.US_ASCII)).put(generation); return out.array();
    }
    private static Identity identity(StoredObjectIdentity value) {
        byte[] bytes = value.copyValue();
        if (bytes.length != 107) throw new IllegalArgumentException("stored identity is invalid");
        return new Identity(Arrays.copyOfRange(bytes, 0, 32), checkedLocator(new String(bytes, 32, 43,
                java.nio.charset.StandardCharsets.US_ASCII)), Arrays.copyOfRange(bytes, 75, 107));
    }
    private static Identity requireIdentity(StorageBindingRef binding, StoredObjectIdentity identity) {
        Identity result = identity(identity);
        if (!MessageDigest.isEqual(bindingFingerprint(binding), result.binding())) throw new IllegalArgumentException("stored identity binding is invalid");
        return result;
    }
    private record Identity(byte[] binding, String name, byte[] generation) { }
    private record Evidence(long length, byte[] sha) { }
    @FunctionalInterface interface ConformanceCheckpointHook { void reached(String checkpoint); }
    private record StoreCommitFence(ReentrantLock fence) implements AutoCloseable {
        @Override public void close() { fence.unlock(); }
    }
    private record Descriptor(long contentVersion, long attempt, long length, byte[] sha, byte[] generation) {
        byte[] bytes() { ByteBuffer value = ByteBuffer.allocate(88); value.putLong(contentVersion).putLong(attempt).putLong(length).put(sha).put(generation); return value.array(); }
        static Descriptor parse(ByteBuffer value) { long version = value.getLong(), attempt = value.getLong(), length = value.getLong(); byte[] sha = new byte[32], generation = new byte[32]; value.get(sha).get(generation); if (version < 1 || attempt < 1 || length < 1) throw new IllegalArgumentException("descriptor is invalid"); return new Descriptor(version, attempt, length, sha, generation); }
    }
    private final class LocalReadHandle implements AttachmentReadHandle {
        private final SecureDirectoryStream<Path> directory; private final SeekableByteChannel channel; private final String name;
        private final Descriptor descriptor; private final InputStream input; private boolean closed;
        LocalReadHandle(SecureDirectoryStream<Path> directory, SeekableByteChannel channel, String name,
                        Descriptor descriptor, long remaining, Instant deadline, Duration idleTimeout) {
            this.directory = directory; this.channel = channel; this.name = name; this.descriptor = descriptor;
            this.input = new TimedInputStream(Channels.newInputStream(channel), deadline, idleTimeout) {
                long left = remaining; boolean completed;
                private void complete() throws IOException {
                    if (completed) return;
                    completed = true;
                    try {
                        // A modification after pre-open verification is observable before this
                        // stream may report the requested range as complete.
                        verifyOpenedContent(LocalReadHandle.this.directory, LocalReadHandle.this.name,
                                LocalReadHandle.this.descriptor, LocalReadHandle.this.channel, deadline);
                    } catch (IOException exception) {
                        throw exception;
                    } catch (Exception exception) {
                        throw new IOException("stored content post-read verification failed", exception);
                    }
                }
                @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                    if (left <= 0) { complete(); return -1; }
                    int count = super.read(bytes, offset, (int) Math.min(left, length));
                    if (count < 0) throw new IOException("stored content was truncated during read");
                    if (count > 0) left -= count;
                    if (left == 0) complete();
                    return count;
                }
                @Override public int read() throws IOException {
                    if (left <= 0) { complete(); return -1; }
                    int value = super.read();
                    if (value < 0) throw new IOException("stored content was truncated during read");
                    left--;
                    if (left == 0) complete();
                    return value;
                }
            };
        }
        @Override public InputStream inputStream() { return input; }
        @Override public void cancel() { try { close(); } catch (IOException ignored) { } }
        @Override public void close() throws IOException { if (!closed) { closed = true; IOException failure = null; try { input.close(); } catch (IOException e) { failure = e; } try { directory.close(); } catch (IOException e) { if (failure == null) failure = e; } if (failure != null) throw failure; } }
    }
    /** Closes the exact stream on idle/total expiry so a slow peer cannot pin a worker thread. */
    private static class TimedInputStream extends FilterInputStream {
        private final Instant deadline; private final Duration idleTimeout; private volatile boolean timedOut;
        private ScheduledFuture<?> expiry;
        TimedInputStream(InputStream input, Instant deadline, Duration idleTimeout) {
            super(input); this.deadline = deadline; this.idleTimeout = idleTimeout; arm();
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            check(); int count = super.read(bytes, offset, length); if (count > 0) arm(); check(); return count;
        }
        @Override public int read() throws IOException {
            check(); int value = super.read(); if (value >= 0) arm(); check(); return value;
        }
        @Override public synchronized void close() throws IOException {
            if (expiry != null) expiry.cancel(false); super.close();
        }
        private synchronized void arm() {
            if (expiry != null) expiry.cancel(false);
            long delay = allowedNanos(deadline, idleTimeout);
            expiry = STREAM_DEADLINES.schedule(() -> {
                timedOut = true;
                closeQuietly(this);
            }, delay, TimeUnit.NANOSECONDS);
        }
        private void check() throws IOException {
            if (timedOut || !Instant.now().isBefore(deadline)) {
                timedOut = true; closeQuietly(this); throw new IOException("attachment stream deadline expired");
            }
        }
    }
}
