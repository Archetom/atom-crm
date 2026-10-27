package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.AttachmentDomainException;
import io.github.archetom.crm.domain.model.AttachmentByteState;
import io.github.archetom.crm.domain.model.AttachmentContentEvidence;
import io.github.archetom.crm.domain.model.AttachmentInspectionState;
import io.github.archetom.crm.domain.model.AttachmentPublicationState;
import io.github.archetom.crm.domain.model.AttachmentUploadOrigin;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.MarketingFileDraftId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * Provider-neutral attachment aggregate. Storage locators, read handles and scanner details are
 * deliberately owned by application work storage rather than this aggregate.
 */
@Getter
public class AttachmentFile extends AggregateRoot<AttachmentFileId> {

    private AttachmentFileId id;
    private TenantId tenantId;
    private AttachmentUploadOrigin uploadOrigin;
    private String displayName;
    private String declaredMediaType;
    private long expectedBytes;
    private byte[] expectedSha256;
    private AttachmentByteState byteState;
    private AttachmentInspectionState inspectionState;
    private AttachmentPublicationState publicationState;
    private long contentVersion;
    private long actualBytes;
    private String mediaType;
    private String controlledReason;
    private LocalDateTime availableTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private AttachmentFile() {
    }

    public record Snapshot(
            AttachmentFileId id, TenantId tenantId, AttachmentUploadOrigin uploadOrigin,
            String displayName, String declaredMediaType, long expectedBytes, byte[] expectedSha256,
            AttachmentByteState byteState, AttachmentInspectionState inspectionState,
            AttachmentPublicationState publicationState, long contentVersion, long actualBytes,
            String mediaType, String controlledReason, LocalDateTime availableTime, Long version,
            LocalDateTime createdTime, LocalDateTime updatedTime) {
        public Snapshot {
            expectedSha256 = expectedSha256 == null ? null : Arrays.copyOf(expectedSha256, 32);
        }

        @Override
        public byte[] expectedSha256() {
            return expectedSha256 == null ? null : Arrays.copyOf(expectedSha256, expectedSha256.length);
        }
    }

    public static AttachmentFile createIntent(
            TenantId tenantId, MemberId uploaderMemberId, String displayName,
            String declaredMediaType, long expectedBytes, byte[] expectedSha256,
            LocalDateTime now) {
        return create(tenantId, new AttachmentUploadOrigin.Member(requireUploader(uploaderMemberId)),
                displayName, declaredMediaType, expectedBytes, expectedSha256, now);
    }

    /** Creates an anonymous candidate attributed to its exact public MarketingForm draft. */
    public static AttachmentFile createPublicMarketingIntent(
            TenantId tenantId, MarketingFileDraftId draftId, String displayName,
            String declaredMediaType, long expectedBytes, byte[] expectedSha256,
            LocalDateTime now) {
        return create(tenantId, new AttachmentUploadOrigin.PublicMarketingForm(draftId),
                displayName, declaredMediaType, expectedBytes, expectedSha256, now);
    }

    private static AttachmentFile create(
            TenantId tenantId, AttachmentUploadOrigin origin, String displayName,
            String declaredMediaType, long expectedBytes, byte[] expectedSha256,
            LocalDateTime now) {
        AttachmentFile file = new AttachmentFile();
        file.tenantId = requireTenant(tenantId);
        file.uploadOrigin = requireOrigin(origin);
        file.displayName = required(displayName, "attachment display name");
        file.declaredMediaType = required(declaredMediaType, "attachment media type");
        if (expectedBytes <= 0) {
            throw new AttachmentDomainException("attachment expected bytes are invalid");
        }
        file.expectedBytes = expectedBytes;
        file.expectedSha256 = copySha(expectedSha256);
        file.byteState = AttachmentByteState.UPLOADING;
        file.inspectionState = AttachmentInspectionState.NOT_RUN;
        file.publicationState = AttachmentPublicationState.PRIVATE;
        file.contentVersion = 1;
        file.createdTime = requiredTime(now);
        file.updatedTime = file.createdTime;
        return file;
    }

    public static AttachmentFile reconstitute(Snapshot snapshot) {
        if (snapshot == null || snapshot.id() == null) {
            throw new AttachmentDomainException("attachment snapshot is invalid");
        }
        AttachmentFile file = new AttachmentFile();
        file.id = snapshot.id();
        file.tenantId = requireTenant(snapshot.tenantId());
        file.uploadOrigin = requireOrigin(snapshot.uploadOrigin());
        file.displayName = required(snapshot.displayName(), "attachment display name");
        file.declaredMediaType = required(snapshot.declaredMediaType(), "attachment media type");
        if (snapshot.expectedBytes() <= 0 || snapshot.contentVersion() <= 0
                || snapshot.actualBytes() < 0 || snapshot.byteState() == null
                || snapshot.inspectionState() == null || snapshot.publicationState() == null) {
            throw new AttachmentDomainException("attachment snapshot state is invalid");
        }
        file.expectedBytes = snapshot.expectedBytes();
        file.expectedSha256 = copySha(snapshot.expectedSha256());
        file.byteState = snapshot.byteState();
        file.inspectionState = snapshot.inspectionState();
        file.publicationState = snapshot.publicationState();
        file.contentVersion = snapshot.contentVersion();
        file.actualBytes = snapshot.actualBytes();
        file.mediaType = snapshot.mediaType();
        file.controlledReason = snapshot.controlledReason();
        file.availableTime = snapshot.availableTime();
        file.createdTime = requiredTime(snapshot.createdTime());
        file.updatedTime = requiredTime(snapshot.updatedTime());
        file.restoreVersion(snapshot.version());
        file.validateState();
        return file;
    }

    public void acceptStored(AttachmentContentEvidence evidence, String detectedMediaType,
                             LocalDateTime now) {
        requireMutableUpload();
        if (evidence.contentVersion() != contentVersion || evidence.actualBytes() != expectedBytes) {
            throw new AttachmentDomainException("attachment stored evidence does not match intent");
        }
        if (expectedSha256 != null && !Arrays.equals(expectedSha256, evidence.sha256())) {
            throw new AttachmentDomainException("attachment checksum does not match intent");
        }
        this.actualBytes = evidence.actualBytes();
        this.mediaType = required(detectedMediaType, "attachment detected media type");
        this.byteState = AttachmentByteState.STORED;
        this.inspectionState = AttachmentInspectionState.PENDING;
        this.controlledReason = null;
        touch(now);
    }

    /**
     * A completed external store may be observed after a concurrent withdrawal.  It can never
     * re-enter normal inspection, but its exact evidence must be retained long enough for the
     * deletion protocol to prove absence.
     */
    public boolean captureStoredForRemoval(AttachmentContentEvidence evidence, String detectedMediaType,
                                           LocalDateTime now) {
        if (byteState != AttachmentByteState.UPLOADING
                || publicationState != AttachmentPublicationState.WITHDRAWN) return false;
        if (evidence == null || evidence.contentVersion() != contentVersion
                || evidence.actualBytes() != expectedBytes
                || expectedSha256 != null && !Arrays.equals(expectedSha256, evidence.sha256())) {
            throw new AttachmentDomainException("attachment stored evidence does not match withdrawn intent");
        }
        actualBytes = evidence.actualBytes();
        mediaType = required(detectedMediaType, "attachment detected media type");
        byteState = AttachmentByteState.REMOVING;
        inspectionState = AttachmentInspectionState.INDETERMINATE;
        controlledReason = "WITHDRAWN_DURING_UPLOAD";
        touch(now);
        return true;
    }

    public void markInspectionClean(LocalDateTime now) {
        requireStored();
        if (inspectionState == AttachmentInspectionState.REJECTED) {
            throw new AttachmentDomainException("rejected attachment cannot become clean");
        }
        inspectionState = AttachmentInspectionState.CLEAN;
        controlledReason = null;
        touch(now);
    }

    public void markInspectionRejected(String reason, LocalDateTime now) {
        requireStored();
        inspectionState = AttachmentInspectionState.REJECTED;
        // An asynchronous inspection can finish after the user has withdrawn the file.
        // Inspection must never make a withdrawn file visible again.
        if (publicationState != AttachmentPublicationState.WITHDRAWN
                && publicationState != AttachmentPublicationState.DELETED) {
            publicationState = AttachmentPublicationState.PRIVATE;
        }
        controlledReason = required(reason, "attachment rejection reason");
        touch(now);
    }

    public void markInspectionIndeterminate(String reason, LocalDateTime now) {
        requireStored();
        if (inspectionState != AttachmentInspectionState.REJECTED) {
            inspectionState = AttachmentInspectionState.INDETERMINATE;
            controlledReason = required(reason, "attachment inspection reason");
            touch(now);
        }
    }

    /**
     * Releases the one durable inspection reservation exactly once when a withdrawal changes the
     * bytes to REMOVING before the scanner can commit. It intentionally does not alter the byte
     * or publication axes: deletion owns those transitions.
     */
    public boolean abandonPendingInspection(String reason, LocalDateTime now) {
        if (inspectionState != AttachmentInspectionState.PENDING) return false;
        inspectionState = AttachmentInspectionState.INDETERMINATE;
        controlledReason = required(reason, "attachment inspection reason");
        touch(now);
        return true;
    }

    public void publish(LocalDateTime now) {
        if (byteState != AttachmentByteState.STORED || inspectionState != AttachmentInspectionState.CLEAN
                || publicationState != AttachmentPublicationState.PRIVATE) {
            throw new AttachmentDomainException("attachment is not eligible for publication");
        }
        publicationState = AttachmentPublicationState.AVAILABLE;
        availableTime = requiredTime(now);
        touch(now);
    }

    public void withdraw(LocalDateTime now) {
        if (publicationState == AttachmentPublicationState.DELETED
                || publicationState == AttachmentPublicationState.WITHDRAWN) {
            return;
        }
        publicationState = AttachmentPublicationState.WITHDRAWN;
        if (byteState == AttachmentByteState.STORED || byteState == AttachmentByteState.UNKNOWN) {
            byteState = AttachmentByteState.REMOVING;
        }
        touch(now);
    }

    public void markRemoved(LocalDateTime now) {
        if (publicationState == AttachmentPublicationState.AVAILABLE) {
            throw new AttachmentDomainException("available attachment must be withdrawn before removal");
        }
        byteState = AttachmentByteState.REMOVED;
        publicationState = AttachmentPublicationState.DELETED;
        touch(now);
    }

    public boolean isAvailable() {
        return byteState == AttachmentByteState.STORED
                && inspectionState == AttachmentInspectionState.CLEAN
                && publicationState == AttachmentPublicationState.AVAILABLE;
    }

    public boolean isUploadedBy(MemberId memberId) {
        return uploadOrigin instanceof AttachmentUploadOrigin.Member member
                && memberId != null && member.memberId().sameValueAs(memberId);
    }

    /** Nullable only for public anonymous candidates; callers must not synthesize a member. */
    public MemberId getUploaderMemberId() {
        return uploadOrigin instanceof AttachmentUploadOrigin.Member member ? member.memberId() : null;
    }

    public void onPersisted(AttachmentFileId id, Long version,
                            LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new AttachmentDomainException("persisted attachment file ID is invalid");
        }
        this.id = id;
        restoreVersion(version);
        if (createdTime != null) {
            this.createdTime = createdTime;
        }
        if (updatedTime != null) {
            this.updatedTime = updatedTime;
        }
    }

    public byte[] getExpectedSha256() {
        return expectedSha256 == null ? null : Arrays.copyOf(expectedSha256, expectedSha256.length);
    }

    private void requireMutableUpload() {
        if (byteState != AttachmentByteState.UPLOADING || publicationState != AttachmentPublicationState.PRIVATE) {
            throw new AttachmentDomainException("attachment upload is no longer active");
        }
    }

    private void requireStored() {
        if (byteState != AttachmentByteState.STORED) {
            throw new AttachmentDomainException("attachment bytes are not stored");
        }
    }

    private void validateState() {
        if (publicationState == AttachmentPublicationState.AVAILABLE
                && (byteState != AttachmentByteState.STORED || inspectionState != AttachmentInspectionState.CLEAN
                || actualBytes <= 0 || mediaType == null)) {
            throw new AttachmentDomainException("available attachment state is invalid");
        }
        if (byteState == AttachmentByteState.REMOVED
                && publicationState != AttachmentPublicationState.DELETED) {
            throw new AttachmentDomainException("removed attachment state is invalid");
        }
    }

    private void touch(LocalDateTime now) {
        updatedTime = requiredTime(now);
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new AttachmentDomainException("attachment tenant is required");
        }
        return value;
    }

    private static MemberId requireUploader(MemberId value) {
        if (value == null) {
            throw new AttachmentDomainException("attachment uploader is required");
        }
        return value;
    }

    private static AttachmentUploadOrigin requireOrigin(AttachmentUploadOrigin value) {
        if (value == null) {
            throw new AttachmentDomainException("attachment upload origin is required");
        }
        return value;
    }

    private static String required(String value, String description) {
        if (value == null || value.isBlank()) {
            throw new AttachmentDomainException(description + " is required");
        }
        return value;
    }

    private static LocalDateTime requiredTime(LocalDateTime value) {
        if (value == null) {
            throw new AttachmentDomainException("attachment time is required");
        }
        return value.withNano(value.getNano() / 1_000 * 1_000);
    }

    private static byte[] copySha(byte[] value) {
        if (value == null) {
            return null;
        }
        if (value.length != 32) {
            throw new AttachmentDomainException("attachment SHA-256 is invalid");
        }
        return Arrays.copyOf(value, value.length);
    }
}
