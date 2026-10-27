package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.ReceivableDomainException;
import io.github.archetom.crm.domain.model.ReceivablePlanStatus;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
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
public class ReceivablePlan extends AggregateRoot<ReceivablePlanId> {
    public static final int MAX_CUSTOM_FIELDS = 200;

    private ReceivablePlanId id;
    private TenantId tenantId;
    private MemberId ownerMemberId;
    private CustomerId customerId;
    private ContractId contractId;
    private int installment;
    private LocalDate dueDate;
    private BigDecimal plannedAmount;
    private String paymentMethod;
    private Integer reminderDays;
    private String remark;
    private ReceivablePlanStatus status;
    private List<ReceivableFieldValue> customFieldValues = List.of();
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private ReceivablePlan() {
    }

    public record Snapshot(
            ReceivablePlanId id, TenantId tenantId, MemberId ownerMemberId,
            CustomerId customerId, ContractId contractId, int installment,
            LocalDate dueDate, BigDecimal plannedAmount, String paymentMethod,
            Integer reminderDays, String remark, ReceivablePlanStatus status,
            List<ReceivableFieldValue> customFieldValues, Long version,
            LocalDateTime createdTime, LocalDateTime updatedTime) {
    }

    public static ReceivablePlan create(
            TenantId tenantId, MemberId ownerMemberId, CustomerId customerId,
            ContractId contractId, int installment, LocalDate dueDate,
            BigDecimal plannedAmount, String paymentMethod, Integer reminderDays,
            String remark, Collection<ReceivableFieldValue> customFieldValues) {
        ReceivablePlan value = new ReceivablePlan();
        value.tenantId = tenant(tenantId);
        value.ownerMemberId = owner(ownerMemberId);
        value.customerId = customer(customerId);
        value.contractId = contract(contractId);
        value.installment = installment(installment);
        value.status = ReceivablePlanStatus.ACTIVE;
        value.applyDetails(dueDate, plannedAmount, paymentMethod, reminderDays, remark,
                customFieldValues);
        value.createdTime = value.updatedTime;
        return value;
    }

    public static ReceivablePlan reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new ReceivableDomainException("receivable plan snapshot must not be empty");
        }
        ReceivablePlan value = new ReceivablePlan();
        value.id = snapshot.id();
        value.tenantId = tenant(snapshot.tenantId());
        value.ownerMemberId = owner(snapshot.ownerMemberId());
        value.customerId = customer(snapshot.customerId());
        value.contractId = contract(snapshot.contractId());
        value.installment = installment(snapshot.installment());
        value.dueDate = Receivable.date(snapshot.dueDate(), "plan due date");
        value.plannedAmount = Receivable.money(snapshot.plannedAmount());
        value.paymentMethod = required(snapshot.paymentMethod(), 50, "payment method");
        value.reminderDays = reminder(snapshot.reminderDays());
        value.remark = optional(snapshot.remark(), 2_000, "remark");
        value.status = snapshot.status() == null ? ReceivablePlanStatus.DELETED : snapshot.status();
        value.customFieldValues = customFields(snapshot.customFieldValues());
        value.restoreVersion(snapshot.version());
        value.createdTime = snapshot.createdTime();
        value.updatedTime = snapshot.updatedTime();
        return value;
    }

    public void replaceDetails(
            LocalDate dueDate, BigDecimal plannedAmount, String paymentMethod,
            Integer reminderDays, String remark,
            Collection<ReceivableFieldValue> customFieldValues) {
        ensureActive();
        applyDetails(dueDate, plannedAmount, paymentMethod, reminderDays, remark,
                customFieldValues);
    }

    public void changeOwner(MemberId ownerMemberId) {
        ensureActive();
        this.ownerMemberId = owner(ownerMemberId);
        touch();
    }

    public void delete() {
        ensureActive();
        status = ReceivablePlanStatus.DELETED;
        touch();
    }

    public boolean isDeleted() {
        return status == ReceivablePlanStatus.DELETED;
    }

    public void onPersisted(
            ReceivablePlanId id, Long version,
            LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new ReceivableDomainException("persisted receivable plan ID is invalid");
        }
        this.id = id;
        restoreVersion(version);
        if (createdTime != null) this.createdTime = createdTime;
        if (updatedTime != null) this.updatedTime = updatedTime;
    }

    private void applyDetails(
            LocalDate dueDate, BigDecimal plannedAmount, String paymentMethod,
            Integer reminderDays, String remark,
            Collection<ReceivableFieldValue> customFieldValues) {
        this.dueDate = Receivable.date(dueDate, "plan due date");
        this.plannedAmount = Receivable.money(plannedAmount);
        this.paymentMethod = required(paymentMethod, 50, "payment method");
        this.reminderDays = reminder(reminderDays);
        this.remark = optional(remark, 2_000, "remark");
        this.customFieldValues = customFields(customFieldValues);
        touch();
    }

    private void ensureActive() {
        if (status != ReceivablePlanStatus.ACTIVE) {
            throw new ReceivableDomainException("deleted receivable plans cannot be changed");
        }
    }

    private void touch() {
        updatedTime = LocalDateTime.now();
    }

    private static int installment(int value) {
        if (value < 1 || value > 10_000) {
            throw new ReceivableDomainException("plan installment is invalid");
        }
        return value;
    }

    private static Integer reminder(Integer value) {
        if (value != null && (value < 0 || value > 3_660)) {
            throw new ReceivableDomainException("plan reminder days is invalid");
        }
        return value;
    }

    private static List<ReceivableFieldValue> customFields(
            Collection<ReceivableFieldValue> values) {
        LinkedHashMap<Object, ReceivableFieldValue> result = new LinkedHashMap<>();
        if (values != null) {
            for (ReceivableFieldValue value : values) {
                if (value == null) throw new ReceivableDomainException("plan custom field is invalid");
                result.put(value.fieldCode(), value);
            }
        }
        if (result.size() > MAX_CUSTOM_FIELDS) {
            throw new ReceivableDomainException("too many plan custom fields");
        }
        return List.copyOf(result.values());
    }

    private static TenantId tenant(TenantId value) {
        if (value == null) throw new ReceivableDomainException("plan tenant is required");
        return value;
    }

    private static MemberId owner(MemberId value) {
        if (value == null) throw new ReceivableDomainException("plan owner is required");
        return value;
    }

    private static CustomerId customer(CustomerId value) {
        if (value == null) throw new ReceivableDomainException("plan customer is required");
        return value;
    }

    private static ContractId contract(ContractId value) {
        if (value == null) throw new ReceivableDomainException("plan contract is required");
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
