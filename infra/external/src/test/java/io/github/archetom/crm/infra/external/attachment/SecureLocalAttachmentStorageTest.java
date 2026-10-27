package io.github.archetom.crm.infra.external.attachment;

import io.github.archetom.crm.application.port.out.attachment.AttachmentByteSource;
import io.github.archetom.crm.application.port.out.attachment.AttachmentRange;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.StorageBindingRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureLocalAttachmentStorageTest {
    @TempDir Path root;

    @Test
    void duplicatePutCannotAbortWinnerAndDeleteIsCrashSafeAndBindingBound() throws Exception {
        SecureLocalAttachmentStorage storage = new SecureLocalAttachmentStorage(root);
        StorageBindingRef bindingA = new StorageBindingRef(new byte[] {1});
        StorageBindingRef bindingB = new StorageBindingRef(new byte[] {2});
        byte[] bytes = new byte[] {1, 2, 3, 4};
        var allocation = storage.allocate(bindingA, bytes.length, Instant.now().plusSeconds(60));
        var command = new AttachmentStorage.StoreCommand(bindingA, allocation.locator(), allocation.storageGeneration(), 1, 1,
                bytes.length, null, Instant.now().plusSeconds(60));

        AttachmentStorage.StoreReceipt first = storage.store(command, source(bytes));
        AttachmentStorage.StoreReceipt retry = storage.store(command, source(bytes));
        assertArrayEquals(first.identity().copyValue(), retry.identity().copyValue());
        assertThrows(AttachmentStorage.StoreConflictException.class,
                () -> storage.store(command, source(new byte[] {4, 3, 2, 1})));
        assertEquals(AttachmentStorage.ProbeState.PRESENT,
                storage.probe(new AttachmentStorage.ProbeCommand(bindingA, allocation.locator(), first.identity(),
                        allocation.storageGeneration(), Instant.now().plusSeconds(60))).state());
        assertThrows(IllegalArgumentException.class, () -> storage.open(new AttachmentStorage.OpenCommand(
                bindingB, first.identity(), first.evidence(), new AttachmentRange(0, bytes.length, bytes.length),
                Instant.now().plusSeconds(60))));

        AttachmentStorage.DeleteCommand delete = new AttachmentStorage.DeleteCommand(bindingA, first.identity(),
                first.evidence(), Instant.now().plusSeconds(60));
        storage.requestDelete(delete);
        storage.requestDelete(delete);
        assertTrue(storage.confirmAbsent(delete));
        assertFalse(storage.probe(new AttachmentStorage.ProbeCommand(bindingA, allocation.locator(), first.identity(),
                allocation.storageGeneration(), Instant.now().plusSeconds(60))).state()
                == AttachmentStorage.ProbeState.PRESENT);
    }

    @Test
    void abortIncompleteNeverTreatsForeignGenerationAsCleanup() throws Exception {
        SecureLocalAttachmentStorage storage = new SecureLocalAttachmentStorage(root);
        StorageBindingRef binding = new StorageBindingRef(new byte[] {1});
        byte[] bytes = new byte[] {1, 2, 3, 4};
        var allocation = storage.allocate(binding, bytes.length, Instant.now().plusSeconds(60));
        AttachmentStorage.StoreReceipt receipt = storage.store(new AttachmentStorage.StoreCommand(binding,
                allocation.locator(), allocation.storageGeneration(), 1, 1, bytes.length, null,
                Instant.now().plusSeconds(60)), source(bytes));
        byte[] foreignGeneration = allocation.storageGeneration().clone();
        foreignGeneration[0] ^= 1;

        assertEquals(AttachmentStorage.AbortState.MISMATCH, storage.abortIncomplete(binding, allocation.locator(),
                foreignGeneration, 1, Instant.now().plusSeconds(60)));
        assertEquals(AttachmentStorage.ProbeState.PRESENT, storage.probe(new AttachmentStorage.ProbeCommand(binding,
                allocation.locator(), receipt.identity(), allocation.storageGeneration(), Instant.now().plusSeconds(60))).state());
    }

    @Test
    void aConfiguredLocalFreeFloorMustBePresentAtConstruction() {
        assertThrows(java.io.IOException.class,
                () -> new SecureLocalAttachmentStorage(root, SecureLocalAttachmentStorage.DEFAULT_MAX_OBJECT_BYTES,
                        Long.MAX_VALUE));
    }

    private static AttachmentByteSource source(byte[] value) {
        byte[] copy = Arrays.copyOf(value, value.length);
        return new AttachmentByteSource() {
            @Override public ByteArrayInputStream openStream() { return new ByteArrayInputStream(copy); }
            @Override public long declaredLength() { return copy.length; }
        };
    }
}
