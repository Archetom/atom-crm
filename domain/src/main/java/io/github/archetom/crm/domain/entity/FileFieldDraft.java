package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.AttachmentDomainException;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.FileFieldDraftState;
import io.github.archetom.crm.domain.valueobject.FileFieldDraftId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaFormId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowRecordId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Arrays;

/** One-shot pre-create staging root for non-scalar FILE field values. */
@Getter
public class FileFieldDraft extends AggregateRoot<FileFieldDraftId> {
    private FileFieldDraftId id;
    private TenantId tenantId;
    private MemberId uploaderMemberId;
    private BusinessObjectType objectType;
    private AttachmentTarget directTarget;
    private StageWorkflowRecordId stageWorkflowRecordId;
    private OaFormId oaFormId;
    private Long oaFormVersion;
    private byte[] purposeDigest;
    private LocalDateTime deadline;
    private FileFieldDraftState state;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private FileFieldDraft() { }

    public record Snapshot(FileFieldDraftId id, TenantId tenantId, MemberId uploaderMemberId,
                           BusinessObjectType objectType, AttachmentTarget directTarget,
                           StageWorkflowRecordId stageWorkflowRecordId,
                           OaFormId oaFormId, Long oaFormVersion, byte[] purposeDigest,
                           LocalDateTime deadline, FileFieldDraftState state, Long version,
                           LocalDateTime createdTime, LocalDateTime updatedTime) {
        public Snapshot { purposeDigest = copy(purposeDigest); }
        @Override public byte[] purposeDigest() { return copy(purposeDigest); }
    }

    public static FileFieldDraft create(TenantId tenantId, MemberId uploaderMemberId,
                                        BusinessObjectType objectType, AttachmentTarget directTarget,
                                        OaFormId oaFormId, Long oaFormVersion, byte[] purposeDigest,
                                        LocalDateTime deadline, LocalDateTime now) {
        FileFieldDraft value = new FileFieldDraft();
        value.tenantId = require(tenantId, "file field draft tenant");
        value.uploaderMemberId = require(uploaderMemberId, "file field draft uploader");
        value.objectType = supported(objectType);
        value.directTarget = checkedTarget(value.objectType, directTarget);
        value.stageWorkflowRecordId = null;
        if (objectType == BusinessObjectType.OA_REQUEST) {
            if (oaFormId == null || oaFormVersion == null || oaFormVersion < 0) {
                throw new AttachmentDomainException("OA file field draft form is invalid");
            }
        } else if (oaFormId != null || oaFormVersion != null) {
            throw new AttachmentDomainException("CRM file field draft cannot carry an OA form");
        }
        value.oaFormId = oaFormId;
        value.oaFormVersion = oaFormVersion;
        value.purposeDigest = requiredDigest(purposeDigest);
        value.deadline = requiredTime(deadline, "file field draft deadline");
        value.createdTime = requiredTime(now, "file field draft time");
        if (!value.deadline.isAfter(value.createdTime)) {
            throw new AttachmentDomainException("file field draft deadline is invalid");
        }
        value.updatedTime = value.createdTime;
        value.state = FileFieldDraftState.ACTIVE;
        return value;
    }

    /** A runtime-stage draft may only be consumed by the exact workflow record that created it. */
    public static FileFieldDraft createStageWorkflow(TenantId tenantId, MemberId uploaderMemberId,
                                                      BusinessObjectType objectType,
                                                      StageWorkflowRecordId workflowRecordId,
                                                      byte[] purposeDigest, LocalDateTime deadline,
                                                      LocalDateTime now) {
        if (objectType == BusinessObjectType.OA_REQUEST || workflowRecordId == null) {
            throw new AttachmentDomainException("stage workflow file field draft scope is invalid");
        }
        FileFieldDraft value = create(tenantId, uploaderMemberId, objectType, null, null, null,
                purposeDigest, deadline, now);
        value.stageWorkflowRecordId = workflowRecordId;
        return value;
    }

    public static FileFieldDraft reconstitute(Snapshot snapshot) {
        if (snapshot == null || snapshot.id() == null) {
            throw new AttachmentDomainException("file field draft snapshot is invalid");
        }
        FileFieldDraft value = new FileFieldDraft();
        value.id = snapshot.id();
        value.tenantId = require(snapshot.tenantId(), "file field draft tenant");
        value.uploaderMemberId = require(snapshot.uploaderMemberId(), "file field draft uploader");
        value.objectType = supported(snapshot.objectType());
        value.directTarget = checkedTarget(value.objectType, snapshot.directTarget());
        value.stageWorkflowRecordId = snapshot.stageWorkflowRecordId();
        value.oaFormId = snapshot.oaFormId();
        value.oaFormVersion = snapshot.oaFormVersion();
        if (value.objectType == BusinessObjectType.OA_REQUEST
                ? value.oaFormId == null || value.oaFormVersion == null || value.oaFormVersion < 0
                : value.oaFormId != null || value.oaFormVersion != null) {
            throw new AttachmentDomainException("file field draft form snapshot is invalid");
        }
        if (value.stageWorkflowRecordId != null
                && (value.objectType == BusinessObjectType.OA_REQUEST || value.directTarget != null)) {
            throw new AttachmentDomainException("stage workflow file field draft snapshot is invalid");
        }
        value.purposeDigest = requiredDigest(snapshot.purposeDigest());
        value.deadline = requiredTime(snapshot.deadline(), "file field draft deadline");
        value.state = snapshot.state() == null ? FileFieldDraftState.EXPIRED : snapshot.state();
        value.restoreVersion(snapshot.version());
        value.createdTime = snapshot.createdTime();
        value.updatedTime = snapshot.updatedTime();
        return value;
    }

    public void consume(MemberId uploader, BusinessObjectType expectedType, LocalDateTime now) {
        requireOwnerAndType(uploader, expectedType, now);
        state = FileFieldDraftState.CONSUMED;
        updatedTime = now;
    }

    public void withdraw(MemberId uploader, LocalDateTime now) {
        requireOwnerAndType(uploader, objectType, now);
        state = FileFieldDraftState.WITHDRAWN;
        updatedTime = now;
    }

    public void expire(LocalDateTime now) {
        if (state == FileFieldDraftState.ACTIVE && now != null && !now.isBefore(deadline)) {
            state = FileFieldDraftState.EXPIRED;
            updatedTime = now;
        }
    }

    public void onPersisted(FileFieldDraftId persistedId, Long persistedVersion,
                            LocalDateTime created, LocalDateTime updated) {
        if (persistedId == null || id != null && !id.sameValueAs(persistedId)) {
            throw new AttachmentDomainException("persisted file field draft ID is invalid");
        }
        id = persistedId;
        restoreVersion(persistedVersion);
        if (created != null) createdTime = created;
        if (updated != null) updatedTime = updated;
    }

    public boolean isActiveFor(MemberId uploader, BusinessObjectType expectedType, LocalDateTime now) {
        return state == FileFieldDraftState.ACTIVE && uploader != null
                && uploader.sameValueAs(uploaderMemberId) && objectType == expectedType
                && now != null && now.isBefore(deadline);
    }

    public boolean isStageWorkflowDraftFor(MemberId uploader, BusinessObjectType expectedType,
                                           StageWorkflowRecordId workflowRecordId,
                                           LocalDateTime now) {
        return stageWorkflowRecordId != null && stageWorkflowRecordId.equals(workflowRecordId)
                && isActiveFor(uploader, expectedType, now);
    }

    private void requireOwnerAndType(MemberId uploader, BusinessObjectType expectedType,
                                     LocalDateTime now) {
        if (!isActiveFor(uploader, expectedType, now)) {
            throw new AttachmentDomainException("file field draft is unavailable");
        }
    }

    private static AttachmentTarget checkedTarget(BusinessObjectType objectType, AttachmentTarget target) {
        if (target != null && (objectType == BusinessObjectType.OA_REQUEST
                || target.type() == AttachmentTargetType.WORK_TASK
                || !target.type().name().equals(objectType.name()))) {
            throw new AttachmentDomainException("file field draft target is invalid");
        }
        return target;
    }

    private static BusinessObjectType supported(BusinessObjectType type) {
        if (type == null) {
            throw new AttachmentDomainException("file field draft object type is unsupported");
        }
        return type;
    }

    private static <T> T require(T value, String description) {
        if (value == null) throw new AttachmentDomainException(description + " is required");
        return value;
    }
    private static LocalDateTime requiredTime(LocalDateTime value, String description) {
        if (value == null) throw new AttachmentDomainException(description + " is required");
        return value;
    }
    private static byte[] requiredDigest(byte[] value) {
        if (value == null || value.length != 32) {
            throw new AttachmentDomainException("file field draft purpose digest is invalid");
        }
        return copy(value);
    }
    private static byte[] copy(byte[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }
}
