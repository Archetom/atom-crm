package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.AttachmentDomainException;
import io.github.archetom.crm.domain.model.MarketingFileDraftState;
import io.github.archetom.crm.domain.valueobject.MarketingFileDraftId;
import io.github.archetom.crm.domain.valueobject.MarketingFormId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Arrays;

/** Hash-only, one-shot public staging root for MarketingForm FILE candidates. */
@Getter
public class MarketingFileDraft extends AggregateRoot<MarketingFileDraftId> {
    private MarketingFileDraftId id;
    private TenantId tenantId;
    private MarketingFormId formId;
    private long formVersion;
    private byte[] credentialDigest;
    private LocalDateTime deadline;
    private MarketingFileDraftState state;
    private long reservedBytes;
    private long actualBytes;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private MarketingFileDraft() { }

    public record Snapshot(MarketingFileDraftId id, TenantId tenantId, MarketingFormId formId,
                           long formVersion, byte[] credentialDigest, LocalDateTime deadline,
                           MarketingFileDraftState state, long reservedBytes, long actualBytes,
                           Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {
        public Snapshot { credentialDigest = copy(credentialDigest); }
        @Override public byte[] credentialDigest() { return copy(credentialDigest); }
    }

    public static MarketingFileDraft create(TenantId tenantId, MarketingFormId formId,
                                             long formVersion, byte[] credentialDigest,
                                             LocalDateTime deadline, LocalDateTime now) {
        MarketingFileDraft value = new MarketingFileDraft();
        value.tenantId = require(tenantId, "marketing file draft tenant");
        value.formId = require(formId, "marketing file draft form");
        if (formVersion < 0) throw new AttachmentDomainException("marketing file draft form version is invalid");
        value.formVersion = formVersion;
        value.credentialDigest = digest(credentialDigest);
        value.deadline = time(deadline, "marketing file draft deadline");
        value.createdTime = time(now, "marketing file draft time");
        if (!value.deadline.isAfter(value.createdTime)) {
            throw new AttachmentDomainException("marketing file draft deadline is invalid");
        }
        value.updatedTime = value.createdTime;
        value.state = MarketingFileDraftState.ACTIVE;
        return value;
    }

    public static MarketingFileDraft reconstitute(Snapshot snapshot) {
        if (snapshot == null || snapshot.id() == null) {
            throw new AttachmentDomainException("marketing file draft snapshot is invalid");
        }
        MarketingFileDraft value = new MarketingFileDraft();
        value.id = snapshot.id();
        value.tenantId = require(snapshot.tenantId(), "marketing file draft tenant");
        value.formId = require(snapshot.formId(), "marketing file draft form");
        if (snapshot.formVersion() < 0 || snapshot.reservedBytes() < 0 || snapshot.actualBytes() < 0) {
            throw new AttachmentDomainException("marketing file draft snapshot is invalid");
        }
        value.formVersion = snapshot.formVersion();
        value.credentialDigest = digest(snapshot.credentialDigest());
        value.deadline = time(snapshot.deadline(), "marketing file draft deadline");
        value.state = snapshot.state() == null ? MarketingFileDraftState.EXPIRED : snapshot.state();
        value.reservedBytes = snapshot.reservedBytes();
        value.actualBytes = snapshot.actualBytes();
        value.restoreVersion(snapshot.version());
        value.createdTime = time(snapshot.createdTime(), "marketing file draft created time");
        value.updatedTime = time(snapshot.updatedTime(), "marketing file draft updated time");
        return value;
    }

    public boolean isActiveFor(MarketingFormId expectedForm, long expectedVersion,
                               LocalDateTime now) {
        return state == MarketingFileDraftState.ACTIVE && expectedForm != null
                && formId.sameValueAs(expectedForm) && formVersion == expectedVersion
                && now != null && now.isBefore(deadline);
    }

    public void reserve(long bytes, int existingCandidates, int maxCandidates, long maxBytes,
                        LocalDateTime now) {
        requireActive(now);
        if (bytes <= 0 || existingCandidates < 0 || existingCandidates >= maxCandidates
                || maxCandidates < 1 || maxBytes < 1 || reservedBytes > maxBytes - bytes) {
            throw new AttachmentDomainException("marketing file draft capacity is unavailable");
        }
        reservedBytes += bytes;
        touch(now);
    }

    public void settleActual(long bytes, long maxBytes, LocalDateTime now) {
        requireActive(now);
        if (bytes <= 0 || maxBytes < 1 || actualBytes > maxBytes - bytes) {
            throw new AttachmentDomainException("marketing file draft capacity is unavailable");
        }
        actualBytes += bytes;
        touch(now);
    }

    public void consume(LocalDateTime now) {
        requireActive(now);
        state = MarketingFileDraftState.CONSUMED;
        touch(now);
    }

    public void withdraw(LocalDateTime now) {
        requireActive(now);
        state = MarketingFileDraftState.WITHDRAWN;
        touch(now);
    }

    public boolean expire(LocalDateTime now) {
        if (state == MarketingFileDraftState.ACTIVE && now != null && !now.isBefore(deadline)) {
            state = MarketingFileDraftState.EXPIRED;
            touch(now);
            return true;
        }
        return false;
    }

    public void onPersisted(MarketingFileDraftId persistedId, Long persistedVersion,
                            LocalDateTime created, LocalDateTime updated) {
        if (persistedId == null || id != null && !id.sameValueAs(persistedId)) {
            throw new AttachmentDomainException("persisted marketing file draft ID is invalid");
        }
        id = persistedId;
        restoreVersion(persistedVersion);
        if (created != null) createdTime = created;
        if (updated != null) updatedTime = updated;
    }

    public byte[] getCredentialDigest() { return copy(credentialDigest); }

    private void requireActive(LocalDateTime now) {
        if (state != MarketingFileDraftState.ACTIVE || now == null || !now.isBefore(deadline)) {
            throw new AttachmentDomainException("marketing file draft is unavailable");
        }
    }
    private void touch(LocalDateTime now) { updatedTime = time(now, "marketing file draft time"); }
    private static <T> T require(T value, String description) {
        if (value == null) throw new AttachmentDomainException(description + " is required");
        return value;
    }
    private static LocalDateTime time(LocalDateTime value, String description) {
        if (value == null) throw new AttachmentDomainException(description + " is required");
        return value;
    }
    private static byte[] digest(byte[] value) {
        if (value == null || value.length != 32) {
            throw new AttachmentDomainException("marketing file draft credential digest is invalid");
        }
        return copy(value);
    }
    private static byte[] copy(byte[] value) { return value == null ? null : Arrays.copyOf(value, value.length); }
}
