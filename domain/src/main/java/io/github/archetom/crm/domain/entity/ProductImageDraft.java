package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.AttachmentDomainException;
import io.github.archetom.crm.domain.model.ProductImageDraftState;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ProductImageDraftId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;

/** Authenticated, one-shot staging root for Product primary/detail image candidates. */
@Getter
public class ProductImageDraft extends AggregateRoot<ProductImageDraftId> {
    private ProductImageDraftId id;
    private TenantId tenantId;
    private MemberId uploaderMemberId;
    private LocalDateTime deadline;
    private ProductImageDraftState state;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private ProductImageDraft() { }

    public record Snapshot(ProductImageDraftId id, TenantId tenantId, MemberId uploaderMemberId,
                           LocalDateTime deadline, ProductImageDraftState state, Long version,
                           LocalDateTime createdTime, LocalDateTime updatedTime) { }

    public static ProductImageDraft create(TenantId tenantId, MemberId uploaderMemberId,
                                           LocalDateTime deadline, LocalDateTime now) {
        ProductImageDraft value = new ProductImageDraft();
        value.tenantId = require(tenantId, "product image draft tenant");
        value.uploaderMemberId = require(uploaderMemberId, "product image draft uploader");
        value.deadline = time(deadline, "product image draft deadline");
        value.createdTime = time(now, "product image draft time");
        if (!value.deadline.isAfter(value.createdTime)) {
            throw new AttachmentDomainException("product image draft deadline is invalid");
        }
        value.updatedTime = value.createdTime;
        value.state = ProductImageDraftState.ACTIVE;
        return value;
    }

    public static ProductImageDraft reconstitute(Snapshot snapshot) {
        if (snapshot == null || snapshot.id() == null) {
            throw new AttachmentDomainException("product image draft snapshot is invalid");
        }
        ProductImageDraft value = new ProductImageDraft();
        value.id = snapshot.id();
        value.tenantId = require(snapshot.tenantId(), "product image draft tenant");
        value.uploaderMemberId = require(snapshot.uploaderMemberId(), "product image draft uploader");
        value.deadline = time(snapshot.deadline(), "product image draft deadline");
        value.state = snapshot.state() == null ? ProductImageDraftState.EXPIRED : snapshot.state();
        value.restoreVersion(snapshot.version());
        value.createdTime = time(snapshot.createdTime(), "product image draft created time");
        value.updatedTime = time(snapshot.updatedTime(), "product image draft updated time");
        return value;
    }

    public boolean isActiveFor(MemberId uploader, LocalDateTime now) {
        return state == ProductImageDraftState.ACTIVE && uploader != null
                && uploader.sameValueAs(uploaderMemberId) && now != null && now.isBefore(deadline);
    }

    public void consume(MemberId uploader, LocalDateTime now) {
        requireActive(uploader, now);
        state = ProductImageDraftState.CONSUMED;
        updatedTime = now;
    }

    public void withdraw(MemberId uploader, LocalDateTime now) {
        requireActive(uploader, now);
        state = ProductImageDraftState.WITHDRAWN;
        updatedTime = now;
    }

    public boolean expire(LocalDateTime now) {
        if (state == ProductImageDraftState.ACTIVE && now != null && !now.isBefore(deadline)) {
            state = ProductImageDraftState.EXPIRED;
            updatedTime = now;
            return true;
        }
        return false;
    }

    public void onPersisted(ProductImageDraftId persistedId, Long persistedVersion,
                            LocalDateTime created, LocalDateTime updated) {
        if (persistedId == null || id != null && !id.sameValueAs(persistedId)) {
            throw new AttachmentDomainException("persisted product image draft ID is invalid");
        }
        id = persistedId;
        restoreVersion(persistedVersion);
        if (created != null) createdTime = created;
        if (updated != null) updatedTime = updated;
    }

    private void requireActive(MemberId uploader, LocalDateTime now) {
        if (!isActiveFor(uploader, now)) {
            throw new AttachmentDomainException("product image draft is unavailable");
        }
    }
    private static <T> T require(T value, String description) {
        if (value == null) throw new AttachmentDomainException(description + " is required");
        return value;
    }
    private static LocalDateTime time(LocalDateTime value, String description) {
        if (value == null) throw new AttachmentDomainException(description + " is required");
        return value;
    }
}
