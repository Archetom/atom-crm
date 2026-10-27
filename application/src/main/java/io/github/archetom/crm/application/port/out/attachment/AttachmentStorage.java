package io.github.archetom.crm.application.port.out.attachment;

import io.github.archetom.crm.domain.model.AttachmentContentEvidence;

import java.time.Instant;
import java.time.Duration;

/** Provider-neutral exact-identity storage contract. No path, URL, SDK or multipart API leaks here. */
public interface AttachmentStorage {

    /** The same immutable upload intent received different bytes or was consumed by another write. */
    class StoreConflictException extends Exception {
        public StoreConflictException(String message) { super(message); }
    }

    Allocation allocate(StorageBindingRef binding, long expectedBytes, Instant deadline);

    StoreReceipt store(StoreCommand command, AttachmentByteSource source) throws Exception;

    ProbeResult probe(ProbeCommand command) throws Exception;

    AttachmentReadHandle open(OpenCommand command) throws Exception;

    void requestDelete(DeleteCommand command) throws Exception;

    boolean confirmAbsent(DeleteCommand command) throws Exception;

    /**
     * Removes only the exact uncommitted attempt.  Callers may settle the durable cleanup job
     * only after {@link AbortState#ABSENT}; a foreign generation is never an implicit success.
     */
    AbortState abortIncomplete(StorageBindingRef binding, PlannedObjectLocator locator, byte[] storageGeneration,
                               long attempt, Instant deadline) throws Exception;

    record Allocation(PlannedObjectLocator locator, byte[] storageGeneration) {
        public Allocation {
            if (locator == null || storageGeneration == null || storageGeneration.length == 0) {
                throw new IllegalArgumentException("storage allocation is invalid");
            }
            storageGeneration = storageGeneration.clone();
        }
        @Override public byte[] storageGeneration() { return storageGeneration.clone(); }
    }
    record StoreCommand(StorageBindingRef binding, PlannedObjectLocator locator, byte[] storageGeneration,
                        long contentVersion, long attempt, long expectedBytes, byte[] expectedSha256, Instant deadline,
                        Duration openTimeout, Duration idleTimeout) {
        public StoreCommand {
            storageGeneration = storageGeneration == null ? null : storageGeneration.clone();
        }
        @Override public byte[] storageGeneration() {
            return storageGeneration == null ? null : storageGeneration.clone();
        }
        public StoreCommand(StorageBindingRef binding, PlannedObjectLocator locator, byte[] storageGeneration,
                            long contentVersion,
                            long attempt, long expectedBytes, byte[] expectedSha256, Instant deadline) {
            this(binding, locator, storageGeneration, contentVersion, attempt, expectedBytes, expectedSha256,
                    deadline, null, null);
        }
    }
    record StoreReceipt(StoredObjectIdentity identity, AttachmentContentEvidence evidence) { }
    record ProbeCommand(StorageBindingRef binding, PlannedObjectLocator locator,
                        StoredObjectIdentity expectedIdentity, byte[] storageGeneration, Instant deadline) {
        public ProbeCommand {
            storageGeneration = storageGeneration == null ? null : storageGeneration.clone();
        }
        @Override public byte[] storageGeneration() {
            return storageGeneration == null ? null : storageGeneration.clone();
        }
    }
    record ProbeResult(ProbeState state, StoredObjectIdentity identity,
                       AttachmentContentEvidence evidence) { }
    enum ProbeState { ABSENT, PRESENT, MISMATCH, UNKNOWN }
    enum AbortState { ABSENT, MISMATCH, UNKNOWN }
    record OpenCommand(StorageBindingRef binding, StoredObjectIdentity identity,
                       AttachmentContentEvidence evidence, AttachmentRange range, Instant deadline,
                       Duration openTimeout, Duration idleTimeout) {
        public OpenCommand(StorageBindingRef binding, StoredObjectIdentity identity,
                           AttachmentContentEvidence evidence, AttachmentRange range, Instant deadline) {
            this(binding, identity, evidence, range, deadline, null, null);
        }
    }
    record DeleteCommand(StorageBindingRef binding, StoredObjectIdentity identity,
                         AttachmentContentEvidence evidence, Instant deadline) { }
}
