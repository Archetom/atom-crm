package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.InvoiceDomainException;
import io.github.archetom.crm.domain.model.InvoiceTitleType;
import io.github.archetom.crm.domain.model.InvoiceProfileStatus;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.InvoiceProfileId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class InvoiceProfile extends AggregateRoot<InvoiceProfileId> {

    public static final int MAX_ACTIVE_PER_CUSTOMER = 100;

    private InvoiceProfileId id;
    private TenantId tenantId;
    private CustomerId customerId;
    private InvoiceTitleType titleType;
    private String title;
    private String taxNumber;
    private String bankName;
    private String bankAccount;
    private String registeredAddress;
    private String registeredPhone;
    private String remark;
    private InvoiceProfileStatus status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private InvoiceProfile() {
    }

    public record Snapshot(
            InvoiceProfileId id, TenantId tenantId, CustomerId customerId,
            InvoiceTitleType titleType, String title, String taxNumber,
            String bankName, String bankAccount, String registeredAddress,
            String registeredPhone, String remark, InvoiceProfileStatus status,
            Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {
    }

    public static InvoiceProfile create(
            TenantId tenantId, CustomerId customerId, InvoiceTitleType titleType,
            String title, String taxNumber, String bankName, String bankAccount,
            String registeredAddress, String registeredPhone, String remark) {
        InvoiceProfile profile = new InvoiceProfile();
        profile.tenantId = require(tenantId, "invoice profile tenant");
        profile.customerId = require(customerId, "invoice profile customer");
        profile.status = InvoiceProfileStatus.ACTIVE;
        profile.apply(titleType, title, taxNumber, bankName, bankAccount,
                registeredAddress, registeredPhone, remark);
        profile.createdTime = profile.updatedTime;
        return profile;
    }

    public static InvoiceProfile reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new InvoiceDomainException("invoice profile snapshot is required");
        }
        InvoiceProfile profile = new InvoiceProfile();
        profile.id = snapshot.id();
        profile.tenantId = require(snapshot.tenantId(), "invoice profile tenant");
        profile.customerId = require(snapshot.customerId(), "invoice profile customer");
        profile.apply(snapshot.titleType(), snapshot.title(), snapshot.taxNumber(),
                snapshot.bankName(), snapshot.bankAccount(), snapshot.registeredAddress(),
                snapshot.registeredPhone(), snapshot.remark());
        profile.status = snapshot.status() == null
                ? InvoiceProfileStatus.DELETED : snapshot.status();
        profile.restoreVersion(snapshot.version());
        profile.createdTime = snapshot.createdTime();
        profile.updatedTime = snapshot.updatedTime();
        return profile;
    }

    public void update(
            InvoiceTitleType titleType, String title, String taxNumber,
            String bankName, String bankAccount, String registeredAddress,
            String registeredPhone, String remark) {
        ensureActive();
        apply(titleType, title, taxNumber, bankName, bankAccount,
                registeredAddress, registeredPhone, remark);
    }

    public void delete() {
        ensureActive();
        status = InvoiceProfileStatus.DELETED;
        touch();
    }

    public void onPersisted(
            InvoiceProfileId id, Long version,
            LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new InvoiceDomainException("persisted invoice profile ID is invalid");
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

    private void apply(
            InvoiceTitleType titleType, String title, String taxNumber,
            String bankName, String bankAccount, String registeredAddress,
            String registeredPhone, String remark) {
        if (titleType == null) {
            throw new InvoiceDomainException("invoice profile title type is required");
        }
        this.titleType = titleType;
        this.title = required(title, 200, "invoice profile title");
        if (titleType == InvoiceTitleType.INDIVIDUAL) {
            this.taxNumber = null;
            this.bankName = null;
            this.bankAccount = null;
            this.registeredAddress = null;
            this.registeredPhone = null;
        } else {
            this.taxNumber = required(taxNumber, 100, "invoice profile tax number");
            this.bankName = optional(bankName, 200, "invoice profile bank name");
            this.bankAccount = optional(bankAccount, 100, "invoice profile bank account");
            this.registeredAddress = optional(
                    registeredAddress, 500, "invoice profile registered address");
            this.registeredPhone = optional(
                    registeredPhone, 50, "invoice profile registered phone");
        }
        this.remark = optional(remark, 1000, "invoice profile remark");
        touch();
    }

    private void ensureActive() {
        if (status != InvoiceProfileStatus.ACTIVE) {
            throw new InvoiceDomainException("deleted invoice profiles cannot be changed");
        }
    }

    private void touch() {
        updatedTime = LocalDateTime.now();
    }

    private static <T> T require(T value, String description) {
        if (value == null) {
            throw new InvoiceDomainException(description + " is required");
        }
        return value;
    }

    private static String required(String value, int max, String description) {
        String normalized = optional(value, max, description);
        if (normalized == null) {
            throw new InvoiceDomainException(description + " is required");
        }
        return normalized;
    }

    private static String optional(String value, int max, String description) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new InvoiceDomainException(description + " is too long");
        }
        return normalized;
    }
}
