package io.github.archetom.crm.infra.external.attachment;

import io.github.archetom.crm.application.port.out.attachment.AttachmentByteSource;
import io.github.archetom.crm.application.port.out.attachment.AttachmentRange;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.StorageBindingRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** S-01/S-04/S-08/S-11 conformance for the first deployable storage profile. */
class AttachmentStorageConformanceTest {
    @TempDir Path root;

    @Test
    void exactStoreProbeAndNativeSingleRangeUseOneSourceOpen() throws Exception {
        SecureLocalAttachmentStorage storage = new SecureLocalAttachmentStorage(root);
        StorageBindingRef binding = new StorageBindingRef(new byte[] {7, 9});
        byte[] bytes = "%PDF-1.7\nbody".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var allocation = storage.allocate(binding, bytes.length, Instant.now().plusSeconds(30));
        var command = new AttachmentStorage.StoreCommand(binding, allocation.locator(), allocation.storageGeneration(), 1, 1, bytes.length,
                null, Instant.now().plusSeconds(30), Duration.ofSeconds(1), Duration.ofSeconds(1));
        var first = storage.store(command, oneShot(bytes));
        var duplicate = storage.store(command, oneShot(bytes));
        assertArrayEquals(first.identity().copyValue(), duplicate.identity().copyValue());
        assertEquals(AttachmentStorage.ProbeState.PRESENT,
                storage.probe(new AttachmentStorage.ProbeCommand(binding, allocation.locator(), first.identity(),
                        allocation.storageGeneration(), Instant.now().plusSeconds(30))).state());
        try (var range = storage.open(new AttachmentStorage.OpenCommand(binding, first.identity(), first.evidence(),
                new AttachmentRange(5, 3, bytes.length), Instant.now().plusSeconds(30), Duration.ofSeconds(1),
                Duration.ofSeconds(1)))) {
            assertArrayEquals("1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII), range.inputStream().readAllBytes());
        }
    }

    @Test
    void partialObjectIsNeverAuthoritativeAbsentAndOpenTimeoutDoesNotBlockCaller() throws Exception {
        SecureLocalAttachmentStorage storage = new SecureLocalAttachmentStorage(root);
        StorageBindingRef binding = new StorageBindingRef(new byte[] {3});
        byte[] bytes = new byte[] {1, 2, 3};
        var allocation = storage.allocate(binding, bytes.length, Instant.now().plusSeconds(30));
        var receipt = storage.store(new AttachmentStorage.StoreCommand(binding, allocation.locator(), allocation.storageGeneration(), 1, 1,
                bytes.length, null, Instant.now().plusSeconds(30)), oneShot(bytes));
        try (var names = Files.list(root)) {
            Files.delete(names.filter(path -> path.getFileName().toString().endsWith(".d")).findFirst().orElseThrow());
        }
        assertEquals(AttachmentStorage.ProbeState.MISMATCH,
                storage.probe(new AttachmentStorage.ProbeCommand(binding, allocation.locator(), receipt.identity(),
                        allocation.storageGeneration(), Instant.now().plusSeconds(30))).state());

        var next = storage.allocate(binding, bytes.length, Instant.now().plusSeconds(30));
        var command = new AttachmentStorage.StoreCommand(binding, next.locator(), next.storageGeneration(), 1, 1, bytes.length,
                null, Instant.now().plusSeconds(30), Duration.ofMillis(5), Duration.ofSeconds(1));
        assertThrows(Exception.class, () -> storage.store(command, new AttachmentByteSource() {
            @Override public ByteArrayInputStream openStream() {
                try { Thread.sleep(100); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                return new ByteArrayInputStream(bytes);
            }
            @Override public long declaredLength() { return bytes.length; }
        }));
    }

    @Test
    void allocationGenerationFencesStoreAndProbeAndRootEvidenceCannotDrift() throws Exception {
        Path protectedRoot = Files.createDirectory(root.resolve("protected"));
        Files.setPosixFilePermissions(protectedRoot, Set.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        SecureLocalAttachmentStorage storage = new SecureLocalAttachmentStorage(protectedRoot);
        StorageBindingRef binding = new StorageBindingRef(new byte[] {8});
        byte[] bytes = new byte[] {9, 8, 7};
        var allocation = storage.allocate(binding, bytes.length, Instant.now().plusSeconds(30));
        var command = new AttachmentStorage.StoreCommand(binding, allocation.locator(), allocation.storageGeneration(),
                1, 1, bytes.length, null, Instant.now().plusSeconds(30));
        var receipt = storage.store(command, oneShot(bytes));
        byte[] wrongGeneration = allocation.storageGeneration(); wrongGeneration[0] ^= 1;
        assertEquals(AttachmentStorage.ProbeState.MISMATCH, storage.probe(new AttachmentStorage.ProbeCommand(
                binding, allocation.locator(), receipt.identity(), wrongGeneration, Instant.now().plusSeconds(30))).state());
        assertThrows(AttachmentStorage.StoreConflictException.class, () -> storage.store(
                new AttachmentStorage.StoreCommand(binding, allocation.locator(), wrongGeneration,
                        1, 1, bytes.length, null, Instant.now().plusSeconds(30)), oneShot(bytes)));
        try (var entries = Files.list(protectedRoot)) {
            for (Path entry : entries.toList()) {
                assertEquals(java.util.Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                        Files.getPosixFilePermissions(entry));
            }
        }
        Path moved = root.resolve("original-root");
        Files.move(protectedRoot, moved);
        Files.createDirectory(protectedRoot);
        assertThrows(IllegalStateException.class,
                () -> storage.allocate(binding, bytes.length, Instant.now().plusSeconds(30)));
    }

    @Test
    void rootMustBeExactlyOwnerOnly0700() throws Exception {
        for (Set<PosixFilePermission> permissions : java.util.List.of(
                Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE),
                Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE),
                Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.OTHERS_EXECUTE))) {
            Path candidate = Files.createDirectory(root.resolve("root-" + permissions.hashCode()));
            Files.setPosixFilePermissions(candidate, permissions);
            assertThrows(java.io.IOException.class, () -> new SecureLocalAttachmentStorage(candidate));
            assertThrows(java.io.IOException.class, () -> SecureLocalAttachmentStorage.rootAttestationDigest(candidate));
        }
    }

    @Test
    void nonOwnerOnlyContentFailsClosedForProbeOpenAndExactDelete() throws Exception {
        SecureLocalAttachmentStorage storage = new SecureLocalAttachmentStorage(root);
        StorageBindingRef binding = new StorageBindingRef(new byte[] {4});
        byte[] bytes = "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var allocation = storage.allocate(binding, bytes.length, Instant.now().plusSeconds(30));
        var receipt = storage.store(new AttachmentStorage.StoreCommand(binding, allocation.locator(), allocation.storageGeneration(),
                1, 1, bytes.length, null, Instant.now().plusSeconds(30)), oneShot(bytes));
        Path content;
        try (var entries = Files.list(root)) {
            content = entries.filter(path -> !path.getFileName().toString().endsWith(".d")).findFirst().orElseThrow();
        }
        Files.setPosixFilePermissions(content, java.util.Set.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ));
        assertThrows(Exception.class, () -> storage.probe(new AttachmentStorage.ProbeCommand(binding,
                allocation.locator(), receipt.identity(), allocation.storageGeneration(), Instant.now().plusSeconds(30))));
        assertThrows(Exception.class, () -> storage.open(new AttachmentStorage.OpenCommand(binding, receipt.identity(),
                receipt.evidence(), null, Instant.now().plusSeconds(30))));
        assertThrows(Exception.class, () -> storage.requestDelete(new AttachmentStorage.DeleteCommand(binding,
                receipt.identity(), receipt.evidence(), Instant.now().plusSeconds(30))));
    }

    @Test
    void duplicateStoreCannotOpenItsOneShotSourceBeforeWinnerDurablyCommits() throws Exception {
        SecureLocalAttachmentStorage storage = new SecureLocalAttachmentStorage(root);
        StorageBindingRef binding = new StorageBindingRef(new byte[] {5});
        byte[] bytes = "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var allocation = storage.allocate(binding, bytes.length, Instant.now().plusSeconds(30));
        var command = new AttachmentStorage.StoreCommand(binding, allocation.locator(), allocation.storageGeneration(),
                1, 1, bytes.length, null, Instant.now().plusSeconds(30));
        CountDownLatch winnerReading = new CountDownLatch(1);
        CountDownLatch releaseWinner = new CountDownLatch(1);
        CountDownLatch duplicateOpened = new CountDownLatch(1);
        AttachmentByteSource winner = new AttachmentByteSource() {
            @Override public InputStream openStream() {
                return new FilterInputStream(new ByteArrayInputStream(bytes)) {
                    @Override public int read(byte[] buffer, int offset, int length) throws java.io.IOException {
                        winnerReading.countDown();
                        try {
                            if (!releaseWinner.await(5, TimeUnit.SECONDS)) throw new java.io.IOException("winner was not released");
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new java.io.IOException("winner interrupted", exception);
                        }
                        return super.read(buffer, offset, length);
                    }
                };
            }
            @Override public long declaredLength() { return bytes.length; }
        };
        AttachmentByteSource duplicate = new AttachmentByteSource() {
            @Override public InputStream openStream() {
                duplicateOpened.countDown();
                return new ByteArrayInputStream(bytes);
            }
            @Override public long declaredLength() { return bytes.length; }
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> storage.store(command, winner));
            assertTrue(winnerReading.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> storage.store(command, duplicate));
            assertFalse(duplicateOpened.await(150, TimeUnit.MILLISECONDS));
            releaseWinner.countDown();
            assertArrayEquals(first.get(5, TimeUnit.SECONDS).identity().copyValue(),
                    second.get(5, TimeUnit.SECONDS).identity().copyValue());
        } finally {
            releaseWinner.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void openRejectsSameLengthTamperingBeforeExposureAndAfterRead() throws Exception {
        SecureLocalAttachmentStorage storage = new SecureLocalAttachmentStorage(root);
        StorageBindingRef binding = new StorageBindingRef(new byte[] {6});
        byte[] bytes = "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var allocation = storage.allocate(binding, bytes.length, Instant.now().plusSeconds(30));
        AttachmentStorage.StoreReceipt receipt = storage.store(new AttachmentStorage.StoreCommand(binding,
                allocation.locator(), allocation.storageGeneration(), 1, 1, bytes.length, null,
                Instant.now().plusSeconds(30)), oneShot(bytes));
        Path content = contentFile(root);
        byte[] tampered = "%PDF-9.9".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        Files.write(content, tampered, StandardOpenOption.WRITE);
        assertThrows(java.io.IOException.class, () -> storage.open(new AttachmentStorage.OpenCommand(binding,
                receipt.identity(), receipt.evidence(), null, Instant.now().plusSeconds(30))));

        Files.write(content, bytes, StandardOpenOption.WRITE);
        try (var handle = storage.open(new AttachmentStorage.OpenCommand(binding, receipt.identity(), receipt.evidence(),
                null, Instant.now().plusSeconds(30)))) {
            Files.write(content, tampered, StandardOpenOption.WRITE);
            assertThrows(java.io.IOException.class, () -> handle.inputStream().readAllBytes());
        }
    }

    @Test
    void refusesUnknownTransportLengthAndObjectsAboveTheSecureLocalCeiling() throws Exception {
        SecureLocalAttachmentStorage storage = new SecureLocalAttachmentStorage(root);
        StorageBindingRef binding = new StorageBindingRef(new byte[] {10});
        assertThrows(IllegalArgumentException.class, () -> storage.allocate(binding,
                SecureLocalAttachmentStorage.DEFAULT_MAX_OBJECT_BYTES + 1, Instant.now().plusSeconds(30)));
        byte[] bytes = new byte[] {1};
        var allocation = storage.allocate(binding, bytes.length, Instant.now().plusSeconds(30));
        var command = new AttachmentStorage.StoreCommand(binding, allocation.locator(), allocation.storageGeneration(),
                1, 1, bytes.length, null, Instant.now().plusSeconds(30));
        assertThrows(AttachmentStorage.StoreConflictException.class,
                () -> storage.store(command, () -> new ByteArrayInputStream(bytes)));
    }

    private static Path contentFile(Path root) throws java.io.IOException {
        try (var entries = Files.list(root)) {
            return entries.filter(path -> !path.getFileName().toString().endsWith(".d")).findFirst().orElseThrow();
        }
    }

    private static AttachmentByteSource oneShot(byte[] bytes) {
        AtomicBoolean opened = new AtomicBoolean();
        return new AttachmentByteSource() {
            @Override public InputStream openStream() {
                if (!opened.compareAndSet(false, true)) throw new AssertionError("source reopened");
                return new ByteArrayInputStream(bytes);
            }
            @Override public long declaredLength() { return bytes.length; }
        };
    }
}
