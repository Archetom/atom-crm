package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.AttachmentDomainException;
import io.github.archetom.crm.domain.model.CrmImportDraftState;
import io.github.archetom.crm.domain.model.CrmImportObjectType;
import io.github.archetom.crm.domain.valueobject.CrmImportDraftId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Tenant-scoped, one-shot import root. The source CSV remains a private controlled attachment
 * and this aggregate stores only its lifecycle and aggregate-safe completion summary.
 */
@Getter
public class CrmImportDraft extends AggregateRoot<CrmImportDraftId> {
    private CrmImportDraftId id;
    private TenantId tenantId;
    private MemberId uploaderMemberId;
    private CrmImportObjectType objectType;
    private LocalDateTime deadline;
    private CrmImportDraftState state;
    private int importedRows;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private CrmImportDraft() { }

    public record Snapshot(CrmImportDraftId id, TenantId tenantId, MemberId uploaderMemberId,
                           CrmImportObjectType objectType, LocalDateTime deadline,
                           CrmImportDraftState state, int importedRows, Long version,
                           LocalDateTime createdTime, LocalDateTime updatedTime) { }

    public static CrmImportDraft create(TenantId tenantId, MemberId uploaderMemberId,
                                        CrmImportObjectType objectType, LocalDateTime deadline,
                                        LocalDateTime now) {
        CrmImportDraft value = new CrmImportDraft();
        value.tenantId = require(tenantId, "CRM import draft tenant");
        value.uploaderMemberId = require(uploaderMemberId, "CRM import draft uploader");
        value.objectType = require(objectType, "CRM import draft object type");
        value.deadline = time(deadline, "CRM import draft deadline");
        value.createdTime = time(now, "CRM import draft time");
        if (!value.deadline.isAfter(value.createdTime)) {
            throw new AttachmentDomainException("CRM import draft deadline is invalid");
        }
        value.state = CrmImportDraftState.ACTIVE;
        value.updatedTime = value.createdTime;
        return value;
    }

    public static CrmImportDraft reconstitute(Snapshot snapshot) {
        if (snapshot == null || snapshot.id() == null) {
            throw new AttachmentDomainException("CRM import draft snapshot is invalid");
        }
        CrmImportDraft value = new CrmImportDraft();
        value.id = snapshot.id();
        value.tenantId = require(snapshot.tenantId(), "CRM import draft tenant");
        value.uploaderMemberId = require(snapshot.uploaderMemberId(), "CRM import draft uploader");
        value.objectType = require(snapshot.objectType(), "CRM import draft object type");
        value.deadline = time(snapshot.deadline(), "CRM import draft deadline");
        value.state = snapshot.state() == null ? CrmImportDraftState.EXPIRED : snapshot.state();
        if (snapshot.importedRows() < 0) {
            throw new AttachmentDomainException("CRM import draft row count is invalid");
        }
        value.importedRows = snapshot.importedRows();
        value.restoreVersion(snapshot.version());
        value.createdTime = time(snapshot.createdTime(), "CRM import draft created time");
        value.updatedTime = time(snapshot.updatedTime(), "CRM import draft updated time");
        return value;
    }

    public boolean isActiveFor(MemberId uploader, LocalDateTime now) {
        return state == CrmImportDraftState.ACTIVE && uploader != null
                && uploader.sameValueAs(uploaderMemberId) && now != null && now.isBefore(deadline);
    }

    public boolean consume(MemberId uploader, int rowCount, LocalDateTime now) {
        requireActive(uploader, now);
        if (rowCount < 0) {
            throw new AttachmentDomainException("CRM import row count is invalid");
        }
        state = CrmImportDraftState.CONSUMED;
        importedRows = rowCount;
        updatedTime = now;
        return true;
    }

    public void withdraw(MemberId uploader, LocalDateTime now) {
        requireActive(uploader, now);
        state = CrmImportDraftState.WITHDRAWN;
        updatedTime = now;
    }

    public boolean expire(LocalDateTime now) {
        if (state == CrmImportDraftState.ACTIVE && now != null && !now.isBefore(deadline)) {
            state = CrmImportDraftState.EXPIRED;
            updatedTime = now;
            return true;
        }
        return false;
    }

    public void onPersisted(CrmImportDraftId persistedId, Long persistedVersion,
                            LocalDateTime created, LocalDateTime updated) {
        if (persistedId == null || id != null && !id.sameValueAs(persistedId)) {
            throw new AttachmentDomainException("persisted CRM import draft ID is invalid");
        }
        id = persistedId;
        restoreVersion(persistedVersion);
        if (created != null) createdTime = created;
        if (updated != null) updatedTime = updated;
    }

    private void requireActive(MemberId uploader, LocalDateTime now) {
        if (!isActiveFor(uploader, now)) {
            throw new AttachmentDomainException("CRM import draft is unavailable");
        }
    }

    private static <T> T require(T value, String description) {
        if (value == null) throw new AttachmentDomainException(description + " is required");
        return value;
    }

    private static LocalDateTime time(LocalDateTime value, String description) {
        if (value == null) throw new AttachmentDomainException(description + " is required");
        return value.withNano(value.getNano() / 1_000 * 1_000);
    }
}
