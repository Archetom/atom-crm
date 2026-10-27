package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.ReturnVisitDomainException;
import io.github.archetom.crm.domain.model.ReturnVisitForm;
import io.github.archetom.crm.domain.model.ReturnVisitSatisfaction;
import io.github.archetom.crm.domain.model.ReturnVisitStatus;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ReturnVisitId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

@Getter
public class ReturnVisit extends AggregateRoot<ReturnVisitId> {
    public static final int MAX_CUSTOM_FIELDS = 200;

    private ReturnVisitId id;
    private TenantId tenantId;
    private MemberId ownerMemberId;
    private CustomerId customerId;
    private ContactId contactId;
    private ContractId contractId;
    private String visitNumber;
    private LocalDateTime visitTime;
    private ReturnVisitForm visitForm;
    private ReturnVisitSatisfaction satisfaction;
    private String remark;
    private ReturnVisitStatus status;
    private List<ReturnVisitFieldValue> customFieldValues = List.of();
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private ReturnVisit() {
    }

    public record Snapshot(ReturnVisitId id, TenantId tenantId, MemberId ownerMemberId,
                           CustomerId customerId, ContactId contactId, ContractId contractId,
                           String visitNumber, LocalDateTime visitTime, String remark,
                           ReturnVisitForm visitForm, ReturnVisitSatisfaction satisfaction,
                           ReturnVisitStatus status, List<ReturnVisitFieldValue> customFieldValues,
                           Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {
    }

    public static ReturnVisit create(TenantId tenantId, MemberId ownerMemberId,
                                     CustomerId customerId, ContactId contactId, ContractId contractId,
                                     String visitNumber, LocalDateTime visitTime, String remark,
                                     ReturnVisitForm visitForm, ReturnVisitSatisfaction satisfaction,
                                     Collection<ReturnVisitFieldValue> customFieldValues) {
        ReturnVisit value = new ReturnVisit();
        value.tenantId = tenant(tenantId);
        value.ownerMemberId = owner(ownerMemberId);
        value.customerId = customer(customerId);
        value.visitNumber = required(visitNumber, 64, "return visit number");
        value.status = ReturnVisitStatus.ACTIVE;
        value.apply(contactId, contractId, visitTime, remark, visitForm, satisfaction, customFieldValues);
        value.createdTime = value.updatedTime;
        return value;
    }

    public static ReturnVisit reconstitute(Snapshot snapshot) {
        if (snapshot == null) throw new ReturnVisitDomainException("return visit snapshot is required");
        ReturnVisit value = new ReturnVisit();
        value.id = snapshot.id();
        value.tenantId = tenant(snapshot.tenantId());
        value.ownerMemberId = owner(snapshot.ownerMemberId());
        value.customerId = customer(snapshot.customerId());
        value.contactId = snapshot.contactId();
        value.contractId = snapshot.contractId();
        value.visitNumber = required(snapshot.visitNumber(), 64, "return visit number");
        value.visitTime = time(snapshot.visitTime());
        value.remark = optional(snapshot.remark(), 2_000, "return visit remark");
        value.visitForm = snapshot.visitForm();
        value.satisfaction = snapshot.satisfaction();
        value.status = snapshot.status() == null ? ReturnVisitStatus.DELETED : snapshot.status();
        value.customFieldValues = custom(snapshot.customFieldValues());
        value.restoreVersion(snapshot.version());
        value.createdTime = snapshot.createdTime();
        value.updatedTime = snapshot.updatedTime();
        return value;
    }

    public void replace(ContactId contactId, ContractId contractId, LocalDateTime visitTime,
                        String remark, ReturnVisitForm visitForm, ReturnVisitSatisfaction satisfaction,
                        Collection<ReturnVisitFieldValue> customFieldValues) {
        ensureActive();
        apply(contactId, contractId, visitTime, remark, visitForm, satisfaction, customFieldValues);
    }

    public void changeOwner(MemberId ownerMemberId) {
        ensureActive();
        ownerMemberId = owner(ownerMemberId);
        this.ownerMemberId = ownerMemberId;
        touch();
    }

    public void delete() {
        ensureActive();
        status = ReturnVisitStatus.DELETED;
        touch();
    }

    public void onPersisted(ReturnVisitId id, Long version, LocalDateTime createdTime,
                            LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new ReturnVisitDomainException("persisted return visit ID is invalid");
        }
        this.id = id;
        restoreVersion(version);
        if (createdTime != null) this.createdTime = createdTime;
        if (updatedTime != null) this.updatedTime = updatedTime;
    }

    private void apply(ContactId contactId, ContractId contractId, LocalDateTime visitTime,
                       String remark, ReturnVisitForm visitForm, ReturnVisitSatisfaction satisfaction,
                       Collection<ReturnVisitFieldValue> customFieldValues) {
        this.contactId = contactId;
        this.contractId = contractId;
        this.visitTime = time(visitTime);
        this.remark = optional(remark, 2_000, "return visit remark");
        this.visitForm = visitForm;
        this.satisfaction = satisfaction;
        this.customFieldValues = custom(customFieldValues);
        touch();
    }

    private void ensureActive() {
        if (status != ReturnVisitStatus.ACTIVE) {
            throw new ReturnVisitDomainException("deleted return visit cannot be changed");
        }
    }

    private void touch() {
        LocalDateTime now = LocalDateTime.now();
        updatedTime = now.withNano(now.getNano() / 1_000 * 1_000);
    }

    private static List<ReturnVisitFieldValue> custom(Collection<ReturnVisitFieldValue> values) {
        LinkedHashMap<Object, ReturnVisitFieldValue> result = new LinkedHashMap<>();
        if (values != null) {
            for (ReturnVisitFieldValue value : values) {
                if (value == null) throw new ReturnVisitDomainException("return visit field value is invalid");
                result.put(value.fieldCode(), value);
            }
        }
        if (result.size() > MAX_CUSTOM_FIELDS) {
            throw new ReturnVisitDomainException("too many return visit field values");
        }
        return List.copyOf(result.values());
    }

    private static TenantId tenant(TenantId value) {
        if (value == null) throw new ReturnVisitDomainException("return visit tenant is required");
        return value;
    }
    private static MemberId owner(MemberId value) {
        if (value == null) throw new ReturnVisitDomainException("return visit owner is required");
        return value;
    }
    private static CustomerId customer(CustomerId value) {
        if (value == null) throw new ReturnVisitDomainException("return visit customer is required");
        return value;
    }
    private static LocalDateTime time(LocalDateTime value) {
        if (value == null || value.getYear() < 1000 || value.getYear() > 9999) {
            throw new ReturnVisitDomainException("return visit time is invalid");
        }
        return value.withNano(value.getNano() / 1_000 * 1_000);
    }
    private static String required(String value, int max, String description) {
        String normalized = optional(value, max, description);
        if (normalized == null) throw new ReturnVisitDomainException(description + " is required");
        return normalized;
    }
    private static String optional(String value, int max, String description) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > max) {
            throw new ReturnVisitDomainException(description + " is too long");
        }
        return normalized;
    }
}
