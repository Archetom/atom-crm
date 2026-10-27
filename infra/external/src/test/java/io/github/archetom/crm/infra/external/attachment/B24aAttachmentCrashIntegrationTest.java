package io.github.archetom.crm.infra.external.attachment;

import io.github.archetom.crm.application.port.out.attachment.AttachmentByteSource;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.StorageBindingRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Crash cut after exact content deletion: repeat confirmation remains convergent. */
class B24aAttachmentCrashIntegrationTest {
    @TempDir Path root;

    @Test
    void repeatedDeleteAfterAnInterruptedDeleteConvergesToAuthoritativeAbsence() throws Exception {
        SecureLocalAttachmentStorage storage = new SecureLocalAttachmentStorage(root);
        StorageBindingRef binding = new StorageBindingRef(new byte[] {44});
        byte[] bytes = new byte[] {2, 4, 6, 8};
        var allocation = storage.allocate(binding, bytes.length, Instant.now().plusSeconds(30));
        var stored = storage.store(new AttachmentStorage.StoreCommand(binding, allocation.locator(), allocation.storageGeneration(), 1, 1,
                bytes.length, null, Instant.now().plusSeconds(30)), source(bytes));
        var delete = new AttachmentStorage.DeleteCommand(binding, stored.identity(), stored.evidence(), Instant.now().plusSeconds(30));

        // Cut the first delete exactly where the content is gone and the descriptor still stands,
        // otherwise this only proves that a completed delete is idempotent.
        try (AutoCloseable cut = SecureLocalAttachmentStorage.installConformanceCheckpointHook(
                checkpoint -> {
                    if ("DELETE_CONTENT_REMOVED".equals(checkpoint)) {
                        throw new IllegalStateException("interrupted after content removal");
                    }
                })) {
            assertThrows(IllegalStateException.class, () -> storage.requestDelete(delete));
        }

        storage.requestDelete(delete);
        assertTrue(storage.confirmAbsent(delete));
        assertEquals(AttachmentStorage.ProbeState.ABSENT,
                storage.probe(new AttachmentStorage.ProbeCommand(binding, allocation.locator(), stored.identity(),
                        allocation.storageGeneration(), Instant.now().plusSeconds(30))).state());
    }

    @Test
    void descriptorCrashCutRemainsMismatchUntilExactDeleteConverges() throws Exception {
        SecureLocalAttachmentStorage storage = new SecureLocalAttachmentStorage(root);
        StorageBindingRef binding = new StorageBindingRef(new byte[] {45});
        byte[] bytes = new byte[] {1, 3, 5, 7};
        var allocation = storage.allocate(binding, bytes.length, Instant.now().plusSeconds(30));
        var stored = storage.store(new AttachmentStorage.StoreCommand(binding, allocation.locator(), allocation.storageGeneration(), 1, 1,
                bytes.length, null, Instant.now().plusSeconds(30)), source(bytes));
        try (var entries = java.nio.file.Files.list(root)) {
            java.nio.file.Files.delete(entries.filter(path -> path.getFileName().toString().endsWith(".d")).findFirst().orElseThrow());
        }
        assertEquals(AttachmentStorage.ProbeState.MISMATCH,
                storage.probe(new AttachmentStorage.ProbeCommand(binding, allocation.locator(), stored.identity(),
                        allocation.storageGeneration(), Instant.now().plusSeconds(30))).state());
        var delete = new AttachmentStorage.DeleteCommand(binding, stored.identity(), stored.evidence(), Instant.now().plusSeconds(30));
        storage.requestDelete(delete);
        assertTrue(storage.confirmAbsent(delete));
    }

    private static AttachmentByteSource source(byte[] bytes) {
        return new AttachmentByteSource() {
            @Override public ByteArrayInputStream openStream() { return new ByteArrayInputStream(bytes); }
            @Override public long declaredLength() { return bytes.length; }
        };
    }
}
