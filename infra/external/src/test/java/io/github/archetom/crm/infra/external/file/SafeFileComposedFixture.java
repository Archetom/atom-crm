package io.github.archetom.crm.infra.external.file;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test-private B24-X/1 model fixture.
 *
 * <p>The core, target, storage and inspection stores are deliberately separate. Restarting a
 * process discards its volatile process object while retaining only the corresponding durable
 * store. Native adapters report observations; only the core transitions file state.</p>
 */
final class SafeFileComposedFixture {

    static final Instant START = Instant.parse("2026-07-20T00:00:00Z");
    static final int OBJECT_BYTES = 4096;
    static final int BUFFER_LIMIT = 64 * 1024;
    static final String REPORT_VERSION = "B24-X/1";
    static final String FORBIDDEN_NAME_CANARY = "PRIVATE-NAME-CANARY";
    static final String FORBIDDEN_LOCATOR_CANARY = "PRIVATE-LOCATOR-CANARY";
    static final String FORBIDDEN_SCANNER_CANARY = "PRIVATE-SCANNER-CANARY";

    private SafeFileComposedFixture() {
    }

    static byte[] benignBytes() {
        byte[] bytes = new byte[OBJECT_BYTES];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) ((index * 31 + 17) & 0xff);
        }
        return bytes;
    }

    enum Tenant {
        A("tenant-a", "member-a", "storage-binding-a", "sa-17",
                "inspection-binding-a", "ia-31"),
        B("tenant-b", "member-b", "storage-binding-b", "sb-23",
                "inspection-binding-b", "ib-47");

        final String id;
        final String member;
        final String storageBinding;
        final String storageRevision;
        final String inspectionBinding;
        final String inspectionRevision;

        Tenant(String id, String member, String storageBinding, String storageRevision,
                String inspectionBinding, String inspectionRevision) {
            this.id = id;
            this.member = member;
            this.storageBinding = storageBinding;
            this.storageRevision = storageRevision;
            this.inspectionBinding = inspectionBinding;
            this.inspectionRevision = inspectionRevision;
        }
    }

    enum Lane {
        XS(StorageProfile.VERSIONED_OBJECT, InspectionProfile.STREAMED),
        XM(StorageProfile.VERSIONED_OBJECT, InspectionProfile.MANAGED_EVENT),
        XL(StorageProfile.SECURE_LOCAL, InspectionProfile.STREAMED);

        final StorageProfile storageProfile;
        final InspectionProfile inspectionProfile;

        Lane(StorageProfile storageProfile, InspectionProfile inspectionProfile) {
            this.storageProfile = storageProfile;
            this.inspectionProfile = inspectionProfile;
        }
    }

    enum StorageProfile {
        VERSIONED_OBJECT,
        SECURE_LOCAL
    }

    enum InspectionProfile {
        STREAMED,
        MANAGED_EVENT
    }

    enum RangeMode {
        FULL_ONLY,
        NATIVE_SINGLE_RANGE
    }

    enum TargetType {
        WORK_TASK,
        CUSTOMER,
        CONTACT,
        LEAD,
        OPPORTUNITY,
        CONTRACT,
        INVOICE,
        RECEIVABLE,
        RECEIVABLE_PLAN
    }

    enum TargetLifecycle {
        ACTIVE,
        PAUSED,
        DELETED
    }

    enum ByteState {
        ABSENT,
        UPLOADING,
        STORED,
        REMOVING,
        REMOVED,
        UNKNOWN
    }

    enum InspectionState {
        NOT_RUN,
        PENDING,
        CLEAN,
        REJECTED,
        INDETERMINATE
    }

    enum PublicationState {
        PRIVATE,
        AVAILABLE,
        WITHDRAWN,
        DELETED
    }

    enum SafeOutcome {
        OK,
        ACCEPTED,
        NOT_FOUND,
        NOT_READY,
        UNSUPPORTED,
        CONFLICT,
        LIMITED,
        STALE,
        CRASHED,
        RANGE_NOT_SATISFIABLE,
        PENDING
    }

    enum TypeObservation {
        ALLOWED_PDF,
        DISALLOWED,
        UNKNOWN,
        DECLARATION_MISMATCH
    }

    enum ScannerObservation {
        NO_THREAT_DETECTED,
        THREAT_DETECTED,
        UNSUPPORTED,
        FAILED
    }

    record TargetRef(Tenant tenant, TargetType type, String id) {
        TargetRef {
            Objects.requireNonNull(tenant);
            Objects.requireNonNull(type);
            Objects.requireNonNull(id);
        }
    }

    record FileRef(Tenant tenant, String id) {
        FileRef {
            Objects.requireNonNull(tenant);
            Objects.requireNonNull(id);
        }
    }

    record Declaration(String displayName, String declaredMediaType, int expectedBytes) {
        Declaration {
            Objects.requireNonNull(displayName);
            Objects.requireNonNull(declaredMediaType);
            if (displayName.isBlank() || expectedBytes < 1) {
                throw new IllegalArgumentException("invalid fixture declaration");
            }
        }

        static Declaration standard() {
            return new Declaration(FORBIDDEN_NAME_CANARY + "-same.pdf",
                    "application/pdf", OBJECT_BYTES);
        }
    }

    record IntentResult(SafeOutcome outcome, FileRef file) {
    }

    record StoreResult(SafeOutcome outcome, StoredReceipt receipt) {
    }

    record StoredReceipt(FileRef file, String locator, String generation, int actualBytes,
                         String contentDigest, String bindingRevision) {
    }

    record InspectionClaim(FileRef file, String attemptId, long fence,
                           String inspectionRevision, String contentDigest) {
    }

    record NativeInspectionEvidence(FileRef file, String attemptId, long fence,
                                    String inspectionRevision, String contentDigest,
                                    TypeObservation type, ScannerObservation scanner,
                                    boolean complete, Instant observedAt) {
    }

    record DeletionClaim(FileRef file, String deletionId, long fence,
                         String storedIdentityDigest) {
    }

    record AbsenceEvidence(FileRef file, String deletionId, long fence,
                           String storedIdentityDigest, String bindingRevision,
                           boolean exactAbsent, boolean handlesDrained) {
    }

    record StreamResult(SafeOutcome outcome, int httpStatus, long offset, long length,
                        String bodyDigest, Set<String> publicHeaders) {

        static StreamResult rejected(SafeOutcome outcome, int status) {
            return new StreamResult(outcome, status, -1, 0, null, Set.of());
        }
    }

    record CoreSnapshot(ByteState bytes, InspectionState inspection,
                        PublicationState publication, boolean currentRelation,
                        boolean tombstone, String plannedIdentityDigest,
                        String storedIdentityDigest, String inspectionJobId,
                        String publicationWorkId, String deletionWorkId,
                        long inspectionFence, long deletionFence,
                        long version, int publicationWrites, int removalWrites) {
    }

    record TenantCounters(int storeAttempts, int storeEffects, int probes,
                          int inspectionReads, int fullOpens, int rangeOpens,
                          long upstreamBytes, int bufferHighWater,
                          int deleteRequests, int deleteEffects,
                          int confirmations, int cancels, int closes,
                          int doubleReleases, int transparentReopens,
                          int invalidRangeOpens, int activeHandles) {
    }

    record InspectionCounters(int opens, int nativeEffects, int closes,
                              int cancels, int activeAdmissions) {
    }

    record SafeReport(String version, String executionLayer, Lane lane, RangeMode rangeMode,
                      Map<Tenant, String> finalStateDigests,
                      Map<Tenant, TenantCounters> storageCounters,
                      Map<Tenant, InspectionCounters> inspectionCounters,
                      int crossTenantMutations, int externalIoInsideTransactions,
                      int forbiddenSinkHits, int storageListCalls,
                      int secondLogicalPublications, int secondLogicalDeletions) {
    }

    static final class DownloadHandle implements AutoCloseable {
        private final StoragePrivateStore store;
        private final long handleId;

        private DownloadHandle(StoragePrivateStore store, long handleId) {
            this.store = store;
            this.handleId = handleId;
        }

        boolean active() {
            return store.isHandleActive(handleId);
        }

        void cancel() {
            store.release(handleId, true);
        }

        @Override
        public void close() {
            store.release(handleId, false);
        }
    }

    static final class World {
        private static final Set<String> PUBLIC_HEADERS = Set.of(
                "Content-Type", "Content-Length", "Content-Disposition",
                "Cache-Control", "X-Content-Type-Options", "Content-Range");

        private final Lane lane;
        private final RangeMode rangeMode;
        private final DurableCoreStore core = new DurableCoreStore();
        private final TargetStore targets = new TargetStore();
        private final StoragePrivateStore storage = new StoragePrivateStore();
        private final InspectionPrivateStore inspector = new InspectionPrivateStore();
        private final EnumMap<Tenant, CallerState> callers = new EnumMap<>(Tenant.class);
        private final EnumMap<Tenant, Boolean> storageReady = new EnumMap<>(Tenant.class);
        private final EnumMap<Tenant, Boolean> inspectionReady = new EnumMap<>(Tenant.class);
        private final EnumMap<Tenant, Integer> fileSequences = new EnumMap<>(Tenant.class);
        private Instant now = START;
        private CoreProcess coreProcess;
        private StorageProcess storageProcess;
        private InspectionProcess inspectionProcess;
        private int activeTransactions;
        private int externalIoInsideTransactions;
        private int crossTenantMutations;
        private int forbiddenSinkHits;
        private int secondLogicalPublications;
        private int secondLogicalDeletions;
        private int targetReads;
        private int coreFileReads;
        private int authorizationChecks;
        private int recordCap = 100;
        private int maxActiveDownloads = 8;
        // Fault injection. Each switch expresses one violation the counters above exist to catch,
        // so a negative-control test can prove the counter fires instead of only asserting zero.
        private boolean injectedCrossTenantParentDelete;
        private boolean injectedRawStateInReport;

        World(Lane lane, RangeMode rangeMode) {
            this.lane = Objects.requireNonNull(lane);
            this.rangeMode = Objects.requireNonNull(rangeMode);
            for (Tenant tenant : Tenant.values()) {
                callers.put(tenant, new CallerState(tenant.member));
                storageReady.put(tenant, true);
                inspectionReady.put(tenant, true);
                fileSequences.put(tenant, 0);
            }
            TargetRef work = defaultTarget(Tenant.A);
            targets.add(work, Tenant.A.member, true);
            TargetRef customer = defaultTarget(Tenant.B);
            targets.add(customer, Tenant.B.member, false);
            restartCore();
            restartStorage();
            restartInspector();
        }

        static World create(Lane lane, RangeMode rangeMode) {
            return new World(lane, rangeMode);
        }

        TargetRef defaultTarget(Tenant tenant) {
            return tenant == Tenant.A
                    ? new TargetRef(tenant, TargetType.WORK_TASK, "work-task-a")
                    : new TargetRef(tenant, TargetType.CUSTOMER, "customer-b");
        }

        TargetRef addTarget(Tenant tenant, TargetType type, String id) {
            TargetRef target = new TargetRef(tenant, type, id);
            targets.add(target, tenant.member, false);
            return target;
        }

        IntentResult createIntent(Tenant tenant) {
            return createIntent(tenant, defaultTarget(tenant), Declaration.standard());
        }

        IntentResult createIntent(Tenant tenant, TargetRef target, Declaration declaration) {
            requireCore();
            SafeOutcome authorization = authorize(tenant, target, Access.WRITE);
            if (authorization != SafeOutcome.OK) {
                return new IntentResult(authorization, null);
            }
            if (!storageReady.get(tenant) || !inspectionReady.get(tenant)) {
                return new IntentResult(SafeOutcome.NOT_READY, null);
            }
            beginTransaction();
            try {
                TargetRow targetRow = targets.rows.get(target);
                if (targetRow == null || targetRow.lifecycle != TargetLifecycle.ACTIVE) {
                    return new IntentResult(SafeOutcome.NOT_FOUND, null);
                }
                long active = core.files.values().stream()
                        .filter(row -> row.target.equals(target))
                        .filter(row -> row.publication != PublicationState.DELETED)
                        .filter(row -> row.bytes != ByteState.REMOVED)
                        .count();
                if (active >= recordCap) {
                    return new IntentResult(SafeOutcome.LIMITED, null);
                }
                int sequence = fileSequences.compute(tenant, (ignored, value) -> value + 1);
                FileRef file = new FileRef(tenant, "file-" + tenant.name().toLowerCase()
                        + "-" + sequence);
                String locator = tenant.storageBinding + ":" + FORBIDDEN_LOCATOR_CANARY
                        + ":" + sequence;
                FileRow row = new FileRow(file, target, tenant.member, declaration, locator,
                        now.plus(Duration.ofMinutes(15)));
                core.files.put(file, row);
                return new IntentResult(SafeOutcome.ACCEPTED, file);
            } finally {
                endTransaction();
            }
        }

        IntentResult createIntentAs(Tenant callerTenant, TargetRef target) {
            return createIntent(callerTenant, target, Declaration.standard());
        }

        StoreResult store(FileRef file, byte[] bytes, boolean crashAfterEffect) {
            requireCore();
            FileRow row = core.files.get(file);
            if (row == null) {
                return new StoreResult(SafeOutcome.NOT_FOUND, null);
            }
            SafeOutcome authorization = authorize(file.tenant(), row.target, Access.WRITE);
            if (authorization != SafeOutcome.OK || !row.uploader.equals(file.tenant().member)) {
                return new StoreResult(SafeOutcome.NOT_FOUND, null);
            }
            if (!storageReady.get(file.tenant())) {
                return new StoreResult(SafeOutcome.NOT_READY, null);
            }
            if (bytes.length == 0 || bytes.length > row.declaration.expectedBytes()) {
                return new StoreResult(SafeOutcome.CONFLICT, null);
            }
            beginTransaction();
            try {
                if (row.bytes == ByteState.ABSENT) {
                    mutable(row, row.target.tenant());
                    row.bytes = ByteState.UPLOADING;
                    row.version++;
                } else if (row.bytes != ByteState.UPLOADING) {
                    return new StoreResult(SafeOutcome.CONFLICT, null);
                }
            } finally {
                endTransaction();
            }
            StoredReceipt receipt = requireStorage().store(row, bytes, this::externalIo);
            if (receipt == null) {
                return new StoreResult(SafeOutcome.CONFLICT, null);
            }
            if (crashAfterEffect) {
                storageProcess = null;
                return new StoreResult(SafeOutcome.CRASHED, null);
            }
            return new StoreResult(SafeOutcome.ACCEPTED, receipt);
        }

        StoreResult reconcileStore(FileRef file) {
            requireCore();
            FileRow row = core.files.get(file);
            if (row == null || row.bytes != ByteState.UPLOADING) {
                return new StoreResult(SafeOutcome.NOT_FOUND, null);
            }
            StoredReceipt receipt = requireStorage().probe(row, this::externalIo);
            return receipt == null
                    ? new StoreResult(SafeOutcome.PENDING, null)
                    : new StoreResult(SafeOutcome.ACCEPTED, receipt);
        }

        SafeOutcome finalizeStored(FileRef file, StoredReceipt receipt, boolean loseCommitAck) {
            requireCore();
            beginTransaction();
            try {
                FileRow row = core.files.get(file);
                if (row == null || receipt == null || !receipt.file().equals(file)
                        || !row.plannedLocator.equals(receipt.locator())
                        || !file.tenant().storageRevision.equals(receipt.bindingRevision())
                        || receipt.actualBytes() != row.declaration.expectedBytes()) {
                    return SafeOutcome.CONFLICT;
                }
                if (row.bytes == ByteState.STORED
                        && Objects.equals(row.stored, receipt)) {
                    if (loseCommitAck) {
                        coreProcess = null;
                        return SafeOutcome.CRASHED;
                    }
                    return SafeOutcome.ACCEPTED;
                }
                if (row.bytes != ByteState.UPLOADING) {
                    return SafeOutcome.CONFLICT;
                }
                TargetRow target = targets.rows.get(row.target);
                mutable(row, row.target.tenant());
                row.stored = receipt;
                row.bytes = target == null || target.lifecycle == TargetLifecycle.DELETED
                        ? ByteState.REMOVING : ByteState.STORED;
                row.inspection = row.bytes == ByteState.STORED
                        ? InspectionState.PENDING : InspectionState.NOT_RUN;
                if (row.bytes == ByteState.STORED && row.inspectionJobId == null) {
                    row.inspectionJobId = "inspection-" + file.id() + "-1";
                }
                if (row.bytes == ByteState.REMOVING) {
                    ensureDeletion(row);
                }
                row.version++;
            } finally {
                endTransaction();
            }
            if (loseCommitAck) {
                coreProcess = null;
                return SafeOutcome.CRASHED;
            }
            return SafeOutcome.ACCEPTED;
        }

        InspectionClaim claimInspection(FileRef file) {
            requireCore();
            if (!inspectionReady.get(file.tenant())) {
                return null;
            }
            beginTransaction();
            try {
                FileRow row = core.files.get(file);
                if (row == null || row.bytes != ByteState.STORED
                        || row.inspection != InspectionState.PENDING || row.stored == null) {
                    return null;
                }
                mutable(row, file.tenant());
                row.inspectionFence++;
                row.inspectionAttempt++;
                row.version++;
                return new InspectionClaim(file,
                        "inspection-" + file.id() + "-" + row.inspectionAttempt,
                        row.inspectionFence, file.tenant().inspectionRevision,
                        row.stored.contentDigest());
            } finally {
                endTransaction();
            }
        }

        NativeInspectionEvidence driveInspection(InspectionClaim claim,
                                                  TypeObservation type,
                                                  ScannerObservation scanner,
                                                  boolean complete,
                                                  boolean crashAfterNativeEffect) {
            if (claim == null) {
                return null;
            }
            FileRow row = core.files.get(claim.file());
            if (row == null || row.stored == null) {
                return null;
            }
            NativeInspectionEvidence evidence = requireInspector().inspect(
                    lane, claim, row, type, scanner, complete, now,
                    requireStorage(), this::externalIo);
            if (crashAfterNativeEffect) {
                inspectionProcess = null;
                return null;
            }
            return evidence;
        }

        NativeInspectionEvidence reconcileInspection(InspectionClaim claim) {
            return requireInspector().replay(claim, this::externalIo);
        }

        SafeOutcome commitInspection(NativeInspectionEvidence evidence) {
            requireCore();
            if (evidence == null) {
                return SafeOutcome.PENDING;
            }
            beginTransaction();
            try {
                FileRow row = core.files.get(evidence.file());
                if (row == null || row.stored == null) {
                    return SafeOutcome.NOT_FOUND;
                }
                if (row.inspectionFence != evidence.fence()
                        || !evidence.attemptId().equals(
                        "inspection-" + evidence.file().id() + "-" + row.inspectionAttempt)
                        || !evidence.inspectionRevision()
                        .equals(evidence.file().tenant().inspectionRevision)
                        || !evidence.contentDigest().equals(row.stored.contentDigest())) {
                    return SafeOutcome.STALE;
                }
                InspectionState conclusion = conclusion(evidence);
                if (row.inspection == InspectionState.REJECTED
                        && conclusion != InspectionState.REJECTED) {
                    return SafeOutcome.STALE;
                }
                if (row.inspection == conclusion) {
                    return SafeOutcome.OK;
                }
                mutable(row, evidence.file().tenant());
                row.inspection = conclusion;
                if (conclusion == InspectionState.CLEAN) {
                    if (row.publicationWorkId == null) {
                        row.publicationWorkId = "publication-" + row.file.id() + "-1";
                    }
                } else {
                    row.publicationWorkId = null;
                    if (row.publication == PublicationState.AVAILABLE) {
                        row.publication = PublicationState.WITHDRAWN;
                        row.currentRelation = false;
                        row.tombstone = true;
                        row.bytes = ByteState.REMOVING;
                        ensureDeletion(row);
                    }
                }
                row.version++;
                return SafeOutcome.OK;
            } finally {
                endTransaction();
            }
        }

        SafeOutcome runPublisher(FileRef file) {
            requireCore();
            beginTransaction();
            try {
                FileRow row = core.files.get(file);
                if (row == null) {
                    return SafeOutcome.NOT_FOUND;
                }
                TargetRow target = targets.rows.get(row.target);
                if (target == null || target.lifecycle == TargetLifecycle.DELETED) {
                    mutable(row, row.target.tenant());
                    row.publication = PublicationState.DELETED;
                    row.currentRelation = false;
                    row.tombstone = true;
                    if (row.stored != null) {
                        row.bytes = ByteState.REMOVING;
                        ensureDeletion(row);
                    }
                    row.publicationWorkId = null;
                    row.version++;
                    return SafeOutcome.NOT_FOUND;
                }
                if (target.lifecycle != TargetLifecycle.ACTIVE) {
                    return SafeOutcome.PENDING;
                }
                if (row.bytes != ByteState.STORED
                        || row.inspection != InspectionState.CLEAN
                        || row.publication != PublicationState.PRIVATE
                        || !row.currentRelation || row.publicationWorkId == null) {
                    return SafeOutcome.PENDING;
                }
                mutable(row, row.target.tenant());
                if (row.publicationWrites == 0) {
                    row.publicationWrites++;
                } else {
                    secondLogicalPublications++;
                }
                row.publication = PublicationState.AVAILABLE;
                row.publicationWorkId = null;
                row.version++;
                return SafeOutcome.OK;
            } finally {
                endTransaction();
            }
        }

        SafeOutcome listAttachments(Tenant tenant, TargetRef target) {
            requireCore();
            SafeOutcome authorization = authorize(tenant, target, Access.READ);
            if (authorization != SafeOutcome.OK) {
                return authorization;
            }
            beginTransaction();
            try {
                coreFileReads++;
                boolean any = core.files.values().stream()
                        .anyMatch(row -> row.target.equals(target)
                                && row.publication == PublicationState.AVAILABLE
                                && row.currentRelation && row.bytes == ByteState.STORED
                                && row.inspection == InspectionState.CLEAN);
                return any ? SafeOutcome.OK : SafeOutcome.ACCEPTED;
            } finally {
                endTransaction();
            }
        }

        StreamResult requestFull(Tenant tenant, TargetRef target, FileRef file) {
            AuthorizedFile authorized = authorizeRead(tenant, target, file);
            if (authorized == null) {
                return StreamResult.rejected(SafeOutcome.NOT_FOUND, 404);
            }
            if (!storageReady.get(tenant)) {
                return StreamResult.rejected(SafeOutcome.NOT_READY, 503);
            }
            StorageRead read = requireStorage().openAndRead(
                    authorized.row, 0, authorized.row.stored.actualBytes(), false,
                    maxActiveDownloads, this::externalIo);
            if (read == null) {
                return StreamResult.rejected(SafeOutcome.LIMITED, 503);
            }
            return new StreamResult(SafeOutcome.OK, 200, 0, read.length(), read.digest(),
                    PUBLIC_HEADERS);
        }

        StreamResult requestRange(Tenant tenant, TargetRef target, FileRef file,
                                  String rangeHeader) {
            AuthorizedFile authorized = authorizeRead(tenant, target, file);
            if (authorized == null) {
                return StreamResult.rejected(SafeOutcome.NOT_FOUND, 404);
            }
            if (rangeMode != RangeMode.NATIVE_SINGLE_RANGE) {
                return StreamResult.rejected(SafeOutcome.RANGE_NOT_SATISFIABLE, 416);
            }
            Optional<NormalizedRange> selected = normalizeRange(
                    rangeHeader, authorized.row.stored.actualBytes());
            if (selected.isEmpty()) {
                return StreamResult.rejected(SafeOutcome.RANGE_NOT_SATISFIABLE, 416);
            }
            if (!storageReady.get(tenant)) {
                return StreamResult.rejected(SafeOutcome.NOT_READY, 503);
            }
            NormalizedRange range = selected.orElseThrow();
            StorageRead read = requireStorage().openAndRead(authorized.row,
                    range.offset(), range.length(), true, maxActiveDownloads,
                    this::externalIo);
            if (read == null) {
                return StreamResult.rejected(SafeOutcome.LIMITED, 503);
            }
            return new StreamResult(SafeOutcome.OK, 206, range.offset(), range.length(),
                    read.digest(), PUBLIC_HEADERS);
        }

        DownloadHandle openSlowFull(Tenant tenant, TargetRef target, FileRef file) {
            AuthorizedFile authorized = authorizeRead(tenant, target, file);
            if (authorized == null || !storageReady.get(tenant)) {
                return null;
            }
            return requireStorage().openHandle(authorized.row, maxActiveDownloads,
                    this::externalIo);
        }

        SafeOutcome withdraw(Tenant tenant, TargetRef target, FileRef file) {
            SafeOutcome authorization = authorize(tenant, target, Access.WRITE);
            if (authorization != SafeOutcome.OK) {
                return authorization;
            }
            beginTransaction();
            try {
                FileRow row = exactFile(target, file);
                if (row == null) {
                    return SafeOutcome.NOT_FOUND;
                }
                closePublication(row, PublicationState.WITHDRAWN, target.tenant());
            } finally {
                endTransaction();
            }
            requireStorage().cancel(file);
            return SafeOutcome.OK;
        }

        SafeOutcome deleteParent(TargetRef target) {
            requireCore();
            beginTransaction();
            try {
                TargetRow targetRow = targets.rows.get(target);
                if (targetRow == null) {
                    return SafeOutcome.NOT_FOUND;
                }
                targetRow.lifecycle = TargetLifecycle.DELETED;
                targetRow.version++;
                for (FileRow row : core.files.values()) {
                    if (injectedCrossTenantParentDelete || row.target.equals(target)) {
                        closePublication(row, PublicationState.DELETED, target.tenant());
                    }
                }
            } finally {
                endTransaction();
            }
            core.files.values().stream()
                    .filter(row -> row.target.equals(target))
                    .forEach(row -> requireStorage().cancel(row.file));
            return SafeOutcome.OK;
        }

        SafeOutcome deleteTask(TargetRef target) {
            if (target.type() != TargetType.WORK_TASK) {
                return SafeOutcome.CONFLICT;
            }
            boolean hasHistory = core.files.values().stream()
                    .anyMatch(row -> row.target.equals(target));
            return hasHistory ? SafeOutcome.CONFLICT : deleteParent(target);
        }

        DeletionClaim claimDeletion(FileRef file) {
            requireCore();
            beginTransaction();
            try {
                FileRow row = core.files.get(file);
                if (row == null || row.bytes != ByteState.REMOVING
                        || row.deletionWorkId == null || row.stored == null) {
                    return null;
                }
                mutable(row, file.tenant());
                row.deletionFence++;
                row.version++;
                return new DeletionClaim(file, row.deletionWorkId, row.deletionFence,
                        storedIdentityDigest(row.stored));
            } finally {
                endTransaction();
            }
        }

        SafeOutcome requestExactDelete(DeletionClaim claim, boolean crashAfterEffect) {
            if (claim == null) {
                return SafeOutcome.NOT_FOUND;
            }
            FileRow row = core.files.get(claim.file());
            if (!currentDeletion(row, claim)) {
                return SafeOutcome.STALE;
            }
            boolean deleted = requireStorage().deleteExact(row.stored, this::externalIo);
            if (!deleted) {
                return SafeOutcome.PENDING;
            }
            if (crashAfterEffect) {
                storageProcess = null;
                return SafeOutcome.CRASHED;
            }
            return SafeOutcome.ACCEPTED;
        }

        AbsenceEvidence confirmExactAbsence(DeletionClaim claim) {
            if (claim == null) {
                return null;
            }
            FileRow row = core.files.get(claim.file());
            if (!currentDeletion(row, claim)) {
                return null;
            }
            return requireStorage().confirm(row.stored, claim, this::externalIo);
        }

        SafeOutcome commitRemoved(AbsenceEvidence evidence, boolean loseCommitAck) {
            requireCore();
            beginTransaction();
            try {
                if (evidence == null) {
                    return SafeOutcome.PENDING;
                }
                FileRow row = core.files.get(evidence.file());
                if (row != null && row.bytes == ByteState.REMOVED && row.stored != null
                        && storedIdentityDigest(row.stored)
                        .equals(evidence.storedIdentityDigest())) {
                    return SafeOutcome.OK;
                }
                if (row == null || row.stored == null
                        || !Objects.equals(row.deletionWorkId, evidence.deletionId())
                        || row.deletionFence != evidence.fence()
                        || !storedIdentityDigest(row.stored)
                        .equals(evidence.storedIdentityDigest())
                        || !row.file.tenant().storageRevision
                        .equals(evidence.bindingRevision())
                        || !evidence.exactAbsent() || !evidence.handlesDrained()) {
                    return SafeOutcome.STALE;
                }
                if (row.bytes != ByteState.REMOVED) {
                    mutable(row, evidence.file().tenant());
                    row.bytes = ByteState.REMOVED;
                    row.deletionWorkId = null;
                    row.removalWrites++;
                    row.version++;
                }
            } finally {
                endTransaction();
            }
            if (loseCommitAck) {
                coreProcess = null;
                return SafeOutcome.CRASHED;
            }
            return SafeOutcome.OK;
        }

        SafeOutcome expireIntent(FileRef file) {
            requireCore();
            FileRow row = core.files.get(file);
            if (row == null || now.isBefore(row.deadline)) {
                return SafeOutcome.PENDING;
            }
            StoredReceipt receipt = requireStorage().probe(row, this::externalIo);
            beginTransaction();
            try {
                mutable(row, file.tenant());
                if (receipt == null) {
                    row.bytes = ByteState.UNKNOWN;
                    row.currentRelation = false;
                    row.tombstone = true;
                    row.publication = PublicationState.WITHDRAWN;
                    row.version++;
                    return SafeOutcome.PENDING;
                }
                row.stored = receipt;
                row.bytes = ByteState.REMOVING;
                row.currentRelation = false;
                row.tombstone = true;
                row.publication = PublicationState.WITHDRAWN;
                ensureDeletion(row);
                row.version++;
                return SafeOutcome.ACCEPTED;
            } finally {
                endTransaction();
            }
        }

        void restartCore() {
            coreProcess = new CoreProcess(core);
        }

        void restartStorage() {
            storageProcess = new StorageProcess(storage);
        }

        void restartInspector() {
            inspectionProcess = new InspectionProcess(inspector);
        }

        void setStorageReady(Tenant tenant, boolean ready) {
            storageReady.put(tenant, ready);
        }

        void setInspectionReady(Tenant tenant, boolean ready) {
            inspectionReady.put(tenant, ready);
        }

        SafeOutcome inspectionCompatibility(InspectionProfile profile) {
            if (profile == InspectionProfile.MANAGED_EVENT
                    && lane.storageProfile == StorageProfile.SECURE_LOCAL) {
                return SafeOutcome.UNSUPPORTED;
            }
            return profile == lane.inspectionProfile
                    ? SafeOutcome.OK : SafeOutcome.UNSUPPORTED;
        }

        void setFilesRead(Tenant tenant, boolean allowed) {
            callers.get(tenant).filesRead = allowed;
        }

        void setFilesWrite(Tenant tenant, boolean allowed) {
            callers.get(tenant).filesWrite = allowed;
        }

        void setMembershipActive(Tenant tenant, boolean active) {
            callers.get(tenant).active = active;
        }

        void setTargetRead(Tenant tenant, TargetRef target, boolean allowed) {
            TargetRow row = targets.rows.get(target);
            if (row != null) {
                if (allowed) {
                    row.readers.add(tenant.member);
                } else {
                    row.readers.remove(tenant.member);
                }
            }
        }

        void setTargetWrite(Tenant tenant, TargetRef target, boolean allowed) {
            TargetRow row = targets.rows.get(target);
            if (row != null) {
                if (allowed) {
                    row.writers.add(tenant.member);
                } else {
                    row.writers.remove(tenant.member);
                }
            }
        }

        void enterPool(TargetRef target) {
            setTargetLifecycle(target, TargetLifecycle.PAUSED);
        }

        void restoreFromPool(TargetRef target) {
            setTargetLifecycle(target, TargetLifecycle.ACTIVE);
        }

        void setTargetLifecycle(TargetRef target, TargetLifecycle lifecycle) {
            TargetRow row = targets.rows.get(target);
            if (row != null) {
                row.lifecycle = lifecycle;
                row.version++;
            }
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        void setRecordCap(int recordCap) {
            this.recordCap = recordCap;
        }

        /**
         * Fault injection for the negative controls. Each switch reproduces one violation the
         * report counters claim never happens, so the claim can be shown to fail when it should.
         */
        void injectCrossTenantParentDelete() {
            injectedCrossTenantParentDelete = true;
        }

        void injectRawStateInReport() {
            injectedRawStateInReport = true;
        }

        void injectStorageEnumeration() {
            storage.listObjects();
        }

        void injectStorageGenerationDrift(FileRef file) {
            FileRow row = core.files.get(file);
            if (row == null || row.stored == null) {
                throw new IllegalArgumentException("file has no stored object to drift");
            }
            requireStorage().driftGeneration(row.stored);
        }

        void injectTransparentReopen() {
            storage.serveOnGenerationMismatch = true;
        }

        void setMaxActiveDownloads(int maxActiveDownloads) {
            this.maxActiveDownloads = maxActiveDownloads;
        }

        CoreSnapshot snapshot(FileRef file) {
            FileRow row = core.files.get(file);
            if (row == null) {
                return null;
            }
            return new CoreSnapshot(row.bytes, row.inspection, row.publication,
                    row.currentRelation, row.tombstone, digest(row.plannedLocator),
                    row.stored == null ? null : storedIdentityDigest(row.stored),
                    row.inspectionJobId, row.publicationWorkId, row.deletionWorkId,
                    row.inspectionFence, row.deletionFence, row.version,
                    row.publicationWrites, row.removalWrites);
        }

        TenantCounters storageCounters(Tenant tenant) {
            return storage.counters(tenant);
        }

        InspectionCounters inspectionCounters(Tenant tenant) {
            return inspector.counters(tenant);
        }

        int targetReads() {
            return targetReads;
        }

        int coreFileReads() {
            return coreFileReads;
        }

        int authorizationChecks() {
            return authorizationChecks;
        }

        boolean targetHasHiddenCrmLink(TargetRef target) {
            TargetRow row = targets.rows.get(target);
            return row != null && row.hiddenCrmLink;
        }

        SafeReport report() {
            Map<Tenant, String> stateDigests = new EnumMap<>(Tenant.class);
            Map<Tenant, TenantCounters> storageProjection = new EnumMap<>(Tenant.class);
            Map<Tenant, InspectionCounters> inspectionProjection = new EnumMap<>(Tenant.class);
            for (Tenant tenant : Tenant.values()) {
                StringBuilder state = new StringBuilder();
                core.files.values().stream()
                        .filter(row -> row.file.tenant() == tenant)
                        .sorted((left, right) -> left.file.id().compareTo(right.file.id()))
                        .forEach(row -> {
                            state.append(row.bytes).append(':')
                                    .append(row.inspection).append(':')
                                    .append(row.publication).append(':')
                                    .append(row.version).append(';');
                            if (injectedRawStateInReport) {
                                state.append(row.declaration.displayName()).append(';');
                            }
                        });
                stateDigests.put(tenant, sink(injectedRawStateInReport
                        ? state.toString() : digest(state.toString())));
                storageProjection.put(tenant, storage.counters(tenant));
                inspectionProjection.put(tenant, inspector.counters(tenant));
            }
            return new SafeReport(REPORT_VERSION, "MODEL", lane, rangeMode,
                    Map.copyOf(stateDigests), Map.copyOf(storageProjection),
                    Map.copyOf(inspectionProjection), crossTenantMutations,
                    externalIoInsideTransactions, forbiddenSinkHits,
                    storage.listCalls, secondLogicalPublications,
                    secondLogicalDeletions);
        }

        private AuthorizedFile authorizeRead(Tenant tenant, TargetRef target, FileRef file) {
            requireCore();
            SafeOutcome authorization = authorize(tenant, target, Access.READ);
            if (authorization != SafeOutcome.OK) {
                return null;
            }
            beginTransaction();
            try {
                coreFileReads++;
                FileRow row = exactFile(target, file);
                if (row == null || row.bytes != ByteState.STORED
                        || row.inspection != InspectionState.CLEAN
                        || row.publication != PublicationState.AVAILABLE
                        || !row.currentRelation || row.stored == null) {
                    return null;
                }
                return new AuthorizedFile(row);
            } finally {
                endTransaction();
            }
        }

        private SafeOutcome authorize(Tenant callerTenant, TargetRef target, Access access) {
            authorizationChecks++;
            CallerState caller = callers.get(callerTenant);
            if (caller == null || !caller.active
                    || access == Access.READ && !caller.filesRead
                    || access == Access.WRITE && !caller.filesWrite) {
                return SafeOutcome.NOT_FOUND;
            }
            targetReads++;
            if (target.tenant() != callerTenant) {
                return SafeOutcome.NOT_FOUND;
            }
            TargetRow targetRow = targets.rows.get(target);
            if (targetRow == null || targetRow.lifecycle != TargetLifecycle.ACTIVE) {
                return SafeOutcome.NOT_FOUND;
            }
            Set<String> allowed = access == Access.READ
                    ? targetRow.readers : targetRow.writers;
            return allowed.contains(caller.member) ? SafeOutcome.OK : SafeOutcome.NOT_FOUND;
        }

        private FileRow exactFile(TargetRef target, FileRef file) {
            if (file == null || file.tenant() != target.tenant()) {
                return null;
            }
            FileRow row = core.files.get(file);
            return row != null && row.target.equals(target) ? row : null;
        }

        private void closePublication(FileRow row, PublicationState state, Tenant actingTenant) {
            mutable(row, actingTenant);
            row.publication = state;
            row.currentRelation = false;
            row.tombstone = true;
            row.publicationWorkId = null;
            if (row.stored != null && row.bytes != ByteState.REMOVED) {
                row.bytes = ByteState.REMOVING;
                ensureDeletion(row);
            }
            row.version++;
        }

        private void ensureDeletion(FileRow row) {
            if (row.deletionWorkId == null) {
                row.deletionWorkId = "deletion-" + row.file.id() + "-1";
            } else if (!row.deletionWorkId.equals("deletion-" + row.file.id() + "-1")) {
                secondLogicalDeletions++;
            }
        }

        private boolean currentDeletion(FileRow row, DeletionClaim claim) {
            return row != null && row.stored != null
                    && row.bytes == ByteState.REMOVING
                    && Objects.equals(row.deletionWorkId, claim.deletionId())
                    && row.deletionFence == claim.fence()
                    && storedIdentityDigest(row.stored)
                    .equals(claim.storedIdentityDigest());
        }

        private InspectionState conclusion(NativeInspectionEvidence evidence) {
            if (!evidence.complete()
                    || evidence.type() == TypeObservation.UNKNOWN
                    || evidence.scanner() == ScannerObservation.UNSUPPORTED
                    || evidence.scanner() == ScannerObservation.FAILED) {
                return InspectionState.INDETERMINATE;
            }
            if (evidence.type() == TypeObservation.DISALLOWED
                    || evidence.type() == TypeObservation.DECLARATION_MISMATCH
                    || evidence.scanner() == ScannerObservation.THREAT_DETECTED) {
                return InspectionState.REJECTED;
            }
            return evidence.type() == TypeObservation.ALLOWED_PDF
                    && evidence.scanner() == ScannerObservation.NO_THREAT_DETECTED
                    ? InspectionState.CLEAN : InspectionState.INDETERMINATE;
        }

        private void beginTransaction() {
            activeTransactions++;
        }

        private void endTransaction() {
            activeTransactions--;
        }

        private void externalIo() {
            if (activeTransactions != 0) {
                externalIoInsideTransactions++;
                throw new IllegalStateException("fixture external I/O inside transaction");
            }
        }

        /**
         * Every file mutation names the tenant whose authority permits it. Worker-driven paths
         * take that tenant from the evidence or claim they act on, and target-scoped paths take
         * it from the target, so a row reached through another tenant's authority is refused
         * here rather than quietly written.
         */
        private FileRow mutable(FileRow row, Tenant actingTenant) {
            if (row.file.tenant() != actingTenant) {
                crossTenantMutations++;
                throw new IllegalStateException("fixture mutation crossed a tenant boundary");
            }
            return row;
        }

        /** Values leaving the model must carry no private fixture value. */
        private String sink(String value) {
            if (value != null && (value.contains(FORBIDDEN_NAME_CANARY)
                    || value.contains(FORBIDDEN_LOCATOR_CANARY)
                    || value.contains(FORBIDDEN_SCANNER_CANARY))) {
                forbiddenSinkHits++;
            }
            return value;
        }

        private CoreProcess requireCore() {
            if (coreProcess == null) {
                throw new IllegalStateException("core process is stopped");
            }
            return coreProcess;
        }

        private StorageProcess requireStorage() {
            if (storageProcess == null) {
                throw new IllegalStateException("storage process is stopped");
            }
            return storageProcess;
        }

        private InspectionProcess requireInspector() {
            if (inspectionProcess == null) {
                throw new IllegalStateException("inspection process is stopped");
            }
            return inspectionProcess;
        }
    }

    private enum Access {
        READ,
        WRITE
    }

    private record AuthorizedFile(FileRow row) {
    }

    private record NormalizedRange(long offset, int length) {
    }

    private record StorageRead(int length, String digest) {
    }

    private static final class CallerState {
        final String member;
        boolean active = true;
        boolean filesRead = true;
        boolean filesWrite = true;

        private CallerState(String member) {
            this.member = member;
        }
    }

    private static final class TargetRow {
        final TargetRef target;
        final Set<String> readers = new HashSet<>();
        final Set<String> writers = new HashSet<>();
        final boolean hiddenCrmLink;
        TargetLifecycle lifecycle = TargetLifecycle.ACTIVE;
        long version = 1;

        private TargetRow(TargetRef target, String member, boolean hiddenCrmLink) {
            this.target = target;
            this.readers.add(member);
            this.writers.add(member);
            this.hiddenCrmLink = hiddenCrmLink;
        }
    }

    private static final class TargetStore {
        final Map<TargetRef, TargetRow> rows = new LinkedHashMap<>();

        void add(TargetRef target, String member, boolean hiddenCrmLink) {
            rows.put(target, new TargetRow(target, member, hiddenCrmLink));
        }
    }

    private static final class FileRow {
        final FileRef file;
        final TargetRef target;
        final String uploader;
        final Declaration declaration;
        final String plannedLocator;
        final Instant deadline;
        ByteState bytes = ByteState.ABSENT;
        InspectionState inspection = InspectionState.NOT_RUN;
        PublicationState publication = PublicationState.PRIVATE;
        boolean currentRelation = true;
        boolean tombstone;
        StoredReceipt stored;
        String inspectionJobId;
        String publicationWorkId;
        String deletionWorkId;
        long inspectionAttempt;
        long inspectionFence;
        long deletionFence;
        long version = 1;
        int publicationWrites;
        int removalWrites;

        private FileRow(FileRef file, TargetRef target, String uploader,
                        Declaration declaration, String plannedLocator, Instant deadline) {
            this.file = file;
            this.target = target;
            this.uploader = uploader;
            this.declaration = declaration;
            this.plannedLocator = plannedLocator;
            this.deadline = deadline;
        }
    }

    private static final class DurableCoreStore {
        final Map<FileRef, FileRow> files = new LinkedHashMap<>();
    }

    private record CoreProcess(DurableCoreStore store) {
    }

    private static final class StoredObject {
        final FileRef file;
        final String locator;
        final String generation;
        final byte[] bytes;
        final String digest;
        final String bindingRevision;
        boolean deleted;

        private StoredObject(FileRef file, String locator, String generation, byte[] bytes,
                             String digest, String bindingRevision) {
            this.file = file;
            this.locator = locator;
            this.generation = generation;
            this.bytes = Arrays.copyOf(bytes, bytes.length);
            this.digest = digest;
            this.bindingRevision = bindingRevision;
        }
    }

    private static final class MutableStorageCounters {
        int storeAttempts;
        int storeEffects;
        int probes;
        int inspectionReads;
        int fullOpens;
        int rangeOpens;
        long upstreamBytes;
        int bufferHighWater;
        int deleteRequests;
        int deleteEffects;
        int confirmations;
        int cancels;
        int closes;
        int doubleReleases;
        int transparentReopens;
        int invalidRangeOpens;
    }

    private static final class HandleState {
        final long id;
        final FileRef file;
        final Tenant tenant;
        boolean active = true;

        private HandleState(long id, FileRef file) {
            this.id = id;
            this.file = file;
            this.tenant = file.tenant();
        }
    }

    private static final class StoragePrivateStore {
        private final Map<String, StoredObject> objects = new HashMap<>();
        final EnumMap<Tenant, MutableStorageCounters> counters =
                new EnumMap<>(Tenant.class);
        final Map<Long, HandleState> handles = new HashMap<>();
        long nextHandle;
        int listCalls;
        boolean serveOnGenerationMismatch;

        StoredObject object(String locator) {
            return objects.get(locator);
        }

        void putObject(String locator, StoredObject object) {
            objects.put(locator, object);
        }

        boolean hasObject(String locator) {
            return objects.containsKey(locator);
        }

        /**
         * Enumerating the object store is not part of the modelled contract: every access is by
         * exact locator. The map is private so that a future enumeration has to come through
         * here, where it is recorded and surfaces in the report.
         */
        Collection<StoredObject> listObjects() {
            listCalls++;
            return List.copyOf(objects.values());
        }

        private StoragePrivateStore() {
            for (Tenant tenant : Tenant.values()) {
                counters.put(tenant, new MutableStorageCounters());
            }
        }

        TenantCounters counters(Tenant tenant) {
            MutableStorageCounters value = counters.get(tenant);
            int active = (int) handles.values().stream()
                    .filter(handle -> handle.tenant == tenant && handle.active)
                    .count();
            return new TenantCounters(value.storeAttempts, value.storeEffects, value.probes,
                    value.inspectionReads, value.fullOpens, value.rangeOpens,
                    value.upstreamBytes, value.bufferHighWater,
                    value.deleteRequests, value.deleteEffects,
                    value.confirmations, value.cancels, value.closes,
                    value.doubleReleases, value.transparentReopens,
                    value.invalidRangeOpens, active);
        }

        boolean isHandleActive(long handleId) {
            HandleState state = handles.get(handleId);
            return state != null && state.active;
        }

        void release(long handleId, boolean cancelled) {
            HandleState state = handles.get(handleId);
            if (state == null) {
                return;
            }
            MutableStorageCounters value = counters.get(state.tenant);
            if (!state.active) {
                value.doubleReleases++;
                return;
            }
            state.active = false;
            if (cancelled) {
                value.cancels++;
            }
            value.closes++;
        }

        void cancel(FileRef file) {
            handles.values().stream()
                    .filter(handle -> handle.file.equals(file) && handle.active)
                    .map(handle -> handle.id)
                    .toList()
                    .forEach(handle -> release(handle, true));
        }
    }

    private static final class StorageProcess {
        private final StoragePrivateStore store;

        private StorageProcess(StoragePrivateStore store) {
            this.store = store;
        }

        StoredReceipt store(FileRow row, byte[] bytes, Runnable ioCheck) {
            ioCheck.run();
            MutableStorageCounters counters = store.counters.get(row.file.tenant());
            counters.storeAttempts++;
            StoredObject existing = store.object(row.plannedLocator);
            String contentDigest = digest(bytes);
            if (existing != null) {
                if (!existing.file.equals(row.file) || existing.deleted
                        || !existing.digest.equals(contentDigest)
                        || existing.bytes.length != bytes.length) {
                    return null;
                }
                return receipt(existing);
            }
            String generation = row.file.tenant().storageRevision + ":g1";
            StoredObject object = new StoredObject(row.file, row.plannedLocator, generation,
                    bytes, contentDigest, row.file.tenant().storageRevision);
            store.putObject(row.plannedLocator, object);
            counters.storeEffects++;
            counters.bufferHighWater = Math.max(counters.bufferHighWater,
                    Math.min(bytes.length, BUFFER_LIMIT));
            return receipt(object);
        }

        StoredReceipt probe(FileRow row, Runnable ioCheck) {
            ioCheck.run();
            store.counters.get(row.file.tenant()).probes++;
            StoredObject object = store.object(row.plannedLocator);
            return object == null || object.deleted ? null : receipt(object);
        }

        void inspectionRead(FileRow row, Runnable ioCheck) {
            ioCheck.run();
            StoredObject object = exact(row.stored);
            if (object == null) {
                throw new IllegalStateException("exact inspection subject missing");
            }
            store.counters.get(row.file.tenant()).inspectionReads++;
        }

        StorageRead openAndRead(FileRow row, long offset, int length, boolean range,
                                int maxActiveDownloads, Runnable ioCheck) {
            DownloadHandle handle = openHandle(row, maxActiveDownloads, ioCheck);
            if (handle == null) {
                return null;
            }
            try (handle) {
                StoredObject object = exact(row.stored);
                if (object == null) {
                    // The object under this locator is no longer the one the row names. Serving
                    // it anyway is a transparent reopen, which is what this counter exists for.
                    object = servable(row);
                    if (object != null) {
                        store.counters.get(row.file.tenant()).transparentReopens++;
                    }
                }
                if (object == null || offset < 0 || length < 1
                        || offset + length > object.bytes.length) {
                    if (range) {
                        store.counters.get(row.file.tenant()).invalidRangeOpens++;
                    }
                    return null;
                }
                MutableStorageCounters counters = store.counters.get(row.file.tenant());
                if (range) {
                    counters.rangeOpens++;
                } else {
                    counters.fullOpens++;
                }
                counters.upstreamBytes += length;
                counters.bufferHighWater = Math.max(counters.bufferHighWater,
                        Math.min(length, BUFFER_LIMIT));
                byte[] selected = Arrays.copyOfRange(object.bytes, (int) offset,
                        (int) offset + length);
                return new StorageRead(length, digest(selected));
            }
        }

        DownloadHandle openHandle(FileRow row, int maxActiveDownloads, Runnable ioCheck) {
            ioCheck.run();
            StoredObject object = servable(row);
            if (object == null) {
                return null;
            }
            long active = store.handles.values().stream().filter(handle -> handle.active).count();
            if (active >= maxActiveDownloads) {
                return null;
            }
            long id = ++store.nextHandle;
            store.handles.put(id, new HandleState(id, row.file));
            return new DownloadHandle(store, id);
        }

        boolean deleteExact(StoredReceipt receipt, Runnable ioCheck) {
            ioCheck.run();
            MutableStorageCounters counters = store.counters.get(receipt.file().tenant());
            counters.deleteRequests++;
            StoredObject object = exact(receipt);
            if (object == null) {
                return store.hasObject(receipt.locator())
                        && store.object(receipt.locator()).deleted;
            }
            object.deleted = true;
            counters.deleteEffects++;
            return true;
        }

        AbsenceEvidence confirm(StoredReceipt receipt, DeletionClaim claim, Runnable ioCheck) {
            ioCheck.run();
            store.counters.get(receipt.file().tenant()).confirmations++;
            StoredObject object = store.object(receipt.locator());
            boolean exactAbsent = object != null && object.file.equals(receipt.file())
                    && object.generation.equals(receipt.generation()) && object.deleted;
            boolean handlesDrained = store.handles.values().stream()
                    .noneMatch(handle -> handle.file.equals(receipt.file()) && handle.active);
            return new AbsenceEvidence(receipt.file(), claim.deletionId(), claim.fence(),
                    storedIdentityDigest(receipt), receipt.bindingRevision(),
                    exactAbsent, handlesDrained);
        }

        void cancel(FileRef file) {
            store.cancel(file);
        }

        /** Exact identity, or — only under injection — whatever now sits at the same locator. */
        private StoredObject servable(FileRow row) {
            StoredObject object = exact(row.stored);
            if (object != null || !store.serveOnGenerationMismatch || row.stored == null) {
                return object;
            }
            StoredObject drifted = store.object(row.stored.locator());
            return drifted == null || drifted.deleted ? null : drifted;
        }

        void driftGeneration(StoredReceipt receipt) {
            StoredObject object = store.object(receipt.locator());
            if (object == null) {
                return;
            }
            store.putObject(receipt.locator(), new StoredObject(object.file, object.locator,
                    object.generation + ":drifted", object.bytes, object.digest,
                    object.bindingRevision));
        }

        private StoredObject exact(StoredReceipt receipt) {
            if (receipt == null) {
                return null;
            }
            StoredObject object = store.object(receipt.locator());
            if (object == null || object.deleted || !object.file.equals(receipt.file())
                    || !object.generation.equals(receipt.generation())
                    || !object.digest.equals(receipt.contentDigest())
                    || !object.bindingRevision.equals(receipt.bindingRevision())) {
                return null;
            }
            return object;
        }

        private StoredReceipt receipt(StoredObject object) {
            return new StoredReceipt(object.file, object.locator, object.generation,
                    object.bytes.length, object.digest, object.bindingRevision);
        }
    }

    private static final class MutableInspectionCounters {
        int opens;
        int nativeEffects;
        int closes;
        int cancels;
        int activeAdmissions;
    }

    private static final class InspectionPrivateStore {
        final Map<String, NativeInspectionEvidence> observations = new HashMap<>();
        final EnumMap<Tenant, MutableInspectionCounters> counters =
                new EnumMap<>(Tenant.class);
        final String rawScannerCanary = FORBIDDEN_SCANNER_CANARY;

        private InspectionPrivateStore() {
            for (Tenant tenant : Tenant.values()) {
                counters.put(tenant, new MutableInspectionCounters());
            }
        }

        InspectionCounters counters(Tenant tenant) {
            MutableInspectionCounters value = counters.get(tenant);
            return new InspectionCounters(value.opens, value.nativeEffects, value.closes,
                    value.cancels, value.activeAdmissions);
        }
    }

    private static final class InspectionProcess {
        private final InspectionPrivateStore store;

        private InspectionProcess(InspectionPrivateStore store) {
            this.store = store;
        }

        NativeInspectionEvidence inspect(Lane lane, InspectionClaim claim, FileRow row,
                                          TypeObservation type,
                                          ScannerObservation scanner, boolean complete,
                                          Instant now, StorageProcess storage,
                                          Runnable ioCheck) {
            ioCheck.run();
            MutableInspectionCounters counters = store.counters.get(claim.file().tenant());
            counters.opens++;
            counters.activeAdmissions++;
            try {
                if (lane.inspectionProfile == InspectionProfile.MANAGED_EVENT
                        && lane.storageProfile != StorageProfile.VERSIONED_OBJECT) {
                    return null;
                }
                if (lane.inspectionProfile == InspectionProfile.STREAMED) {
                    storage.inspectionRead(row, ioCheck);
                } else {
                    storage.probe(row, ioCheck);
                }
                NativeInspectionEvidence evidence = new NativeInspectionEvidence(
                        claim.file(), claim.attemptId(), claim.fence(),
                        claim.inspectionRevision(), claim.contentDigest(), type, scanner,
                        complete, now);
                store.observations.put(claim.attemptId(), evidence);
                counters.nativeEffects++;
                return evidence;
            } finally {
                counters.activeAdmissions--;
                counters.closes++;
            }
        }

        NativeInspectionEvidence replay(InspectionClaim claim, Runnable ioCheck) {
            ioCheck.run();
            return store.observations.get(claim.attemptId());
        }
    }

    private static final Pattern RANGE_PATTERN = Pattern.compile("([0-9]*)-([0-9]*)");

    private static Optional<NormalizedRange> normalizeRange(String header, int size) {
        if (header == null || !header.startsWith("bytes=") || header.indexOf(',') >= 0) {
            return Optional.empty();
        }
        Matcher matcher = RANGE_PATTERN.matcher(header.substring("bytes=".length()));
        if (!matcher.matches() || matcher.group(1).isEmpty() && matcher.group(2).isEmpty()) {
            return Optional.empty();
        }
        try {
            String firstPart = matcher.group(1);
            String lastPart = matcher.group(2);
            if (firstPart.isEmpty()) {
                long suffix = Long.parseLong(lastPart);
                if (suffix < 1) {
                    return Optional.empty();
                }
                int length = (int) Math.min(suffix, size);
                return Optional.of(new NormalizedRange(size - length, length));
            }
            long first = Long.parseLong(firstPart);
            if (first >= size) {
                return Optional.empty();
            }
            long requestedLast = lastPart.isEmpty()
                    ? size - 1L : Long.parseLong(lastPart);
            if (requestedLast < first) {
                return Optional.empty();
            }
            long last = Math.min(requestedLast, size - 1L);
            return Optional.of(new NormalizedRange(first, Math.toIntExact(last - first + 1)));
        } catch (ArithmeticException | NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static String storedIdentityDigest(StoredReceipt receipt) {
        return digest(receipt.file().tenant().id + ":" + receipt.locator() + ":"
                + receipt.generation() + ":" + receipt.contentDigest());
    }

    private static String digest(String value) {
        return digest(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String digest(byte[] value) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder result = new StringBuilder(64);
            for (byte element : hashed) {
                result.append(String.format("%02x", element));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
