package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.ReceivableDomainException;
import io.github.archetom.crm.domain.model.ReceivableStatus;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ReceivableId;
import io.github.archetom.crm.domain.valueobject.ReceivablePlanId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

@Getter
public class Receivable extends AggregateRoot<ReceivableId> {
    public static final int MAX_CUSTOM_FIELDS = 200;
    private static final LocalDate MINIMUM_DATE = LocalDate.of(1000, 1, 1);
    private static final LocalDate MAXIMUM_DATE = LocalDate.of(9999, 12, 31);

    private ReceivableId id;
    private TenantId tenantId;
    private MemberId ownerMemberId;
    private CustomerId customerId;
    private ContractId contractId;
    private ReceivablePlanId planId;
    private String receiptNumber;
    private LocalDate receivedDate;
    private String paymentMethod;
    private BigDecimal amount;
    private String remark;
    private ReceivableStatus status;
    private List<ReceivableFieldValue> customFieldValues = List.of();
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private Receivable() {
    }

    public record Snapshot(
            ReceivableId id, TenantId tenantId, MemberId ownerMemberId,
            CustomerId customerId, ContractId contractId, ReceivablePlanId planId,
            String receiptNumber, LocalDate receivedDate, String paymentMethod,
            BigDecimal amount, String remark, ReceivableStatus status,
            List<ReceivableFieldValue> customFieldValues, Long version,
            LocalDateTime createdTime, LocalDateTime updatedTime) {
    }

    public static Receivable create(
            TenantId tenantId, MemberId ownerMemberId, CustomerId customerId,
            ContractId contractId, ReceivablePlanId planId, String receiptNumber,
            LocalDate receivedDate, String paymentMethod, BigDecimal amount, String remark,
            Collection<ReceivableFieldValue> customFieldValues) {
        Receivable value = new Receivable();
        value.tenantId = tenant(tenantId);
        value.ownerMemberId = owner(ownerMemberId);
        value.customerId = customer(customerId);
        value.contractId = contract(contractId);
        value.receiptNumber = required(receiptNumber, 64, "receipt number");
        value.status = ReceivableStatus.ACTIVE;
        value.applyDetails(planId, receivedDate, paymentMethod, amount, remark,
                customFieldValues);
        value.createdTime = value.updatedTime;
        return value;
    }

    public static Receivable reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new ReceivableDomainException("receivable snapshot must not be empty");
        }
        Receivable value = new Receivable();
        value.id = snapshot.id();
        value.tenantId = tenant(snapshot.tenantId());
        value.ownerMemberId = owner(snapshot.ownerMemberId());
        value.customerId = customer(snapshot.customerId());
        value.contractId = contract(snapshot.contractId());
        value.planId = snapshot.planId();
        value.receiptNumber = required(snapshot.receiptNumber(), 64, "receipt number");
        value.receivedDate = date(snapshot.receivedDate(), "received date");
        value.paymentMethod = required(snapshot.paymentMethod(), 50, "payment method");
        value.amount = money(snapshot.amount());
        value.remark = optional(snapshot.remark(), 2_000, "remark");
        value.status = snapshot.status() == null ? ReceivableStatus.DELETED : snapshot.status();
        value.customFieldValues = customFields(snapshot.customFieldValues());
        value.restoreVersion(snapshot.version());
        value.createdTime = snapshot.createdTime();
        value.updatedTime = snapshot.updatedTime();
        return value;
    }

    public void replaceDetails(
            ReceivablePlanId planId, LocalDate receivedDate, String paymentMethod,
            BigDecimal amount, String remark,
            Collection<ReceivableFieldValue> customFieldValues) {
        ensureActive();
        applyDetails(planId, receivedDate, paymentMethod, amount, remark, customFieldValues);
    }

    public void changeOwner(MemberId ownerMemberId) {
        ensureActive();
        this.ownerMemberId = owner(ownerMemberId);
        touch();
    }

    public void delete() {
        ensureActive();
        status = ReceivableStatus.DELETED;
        touch();
    }

    public boolean isDeleted() {
        return status == ReceivableStatus.DELETED;
    }

    public void onPersisted(
            ReceivableId id, Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new ReceivableDomainException("persisted receivable ID is invalid");
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

    private void applyDetails(
            ReceivablePlanId planId, LocalDate receivedDate, String paymentMethod,
            BigDecimal amount, String remark,
            Collection<ReceivableFieldValue> customFieldValues) {
        this.planId = planId;
        this.receivedDate = date(receivedDate, "received date");
        this.paymentMethod = required(paymentMethod, 50, "payment method");
        this.amount = money(amount);
        this.remark = optional(remark, 2_000, "remark");
        this.customFieldValues = customFields(customFieldValues);
        touch();
    }

    private void ensureActive() {
        if (status != ReceivableStatus.ACTIVE) {
            throw new ReceivableDomainException("deleted receivables cannot be changed");
        }
    }

    private void touch() {
        updatedTime = LocalDateTime.now();
    }

    static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new ReceivableDomainException("receivable amount is invalid");
        }
        try {
            BigDecimal normalized = value.setScale(2);
            if (normalized.precision() > 22) {
                throw new ReceivableDomainException("receivable amount is invalid");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new ReceivableDomainException("receivable amount supports two decimal places");
        }
    }

    static LocalDate date(LocalDate value, String description) {
        if (value == null || value.isBefore(MINIMUM_DATE) || value.isAfter(MAXIMUM_DATE)) {
            throw new ReceivableDomainException(description + " is invalid");
        }
        return value;
    }

    private static List<ReceivableFieldValue> customFields(
            Collection<ReceivableFieldValue> values) {
        LinkedHashMap<Object, ReceivableFieldValue> result = new LinkedHashMap<>();
        if (values != null) {
            for (ReceivableFieldValue value : values) {
                if (value == null) {
                    throw new ReceivableDomainException("receivable custom field is invalid");
                }
                result.put(value.fieldCode(), value);
            }
        }
        if (result.size() > MAX_CUSTOM_FIELDS) {
            throw new ReceivableDomainException("too many receivable custom fields");
        }
        return List.copyOf(result.values());
    }

    private static TenantId tenant(TenantId value) {
        if (value == null) throw new ReceivableDomainException("receivable tenant is required");
        return value;
    }

    private static MemberId owner(MemberId value) {
        if (value == null) throw new ReceivableDomainException("receivable owner is required");
        return value;
    }

    private static CustomerId customer(CustomerId value) {
        if (value == null) throw new ReceivableDomainException("receivable customer is required");
        return value;
    }

    private static ContractId contract(ContractId value) {
        if (value == null) throw new ReceivableDomainException("receivable contract is required");
        return value;
    }

    private static String required(String value, int max, String description) {
        String normalized = optional(value, max, description);
        if (normalized == null) throw new ReceivableDomainException(description + " is required");
        return normalized;
    }

    private static String optional(String value, int max, String description) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > max) {
            throw new ReceivableDomainException(description + " is too long");
        }
        return normalized;
    }
}
