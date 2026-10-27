package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.OpportunityDomainException;
import io.github.archetom.crm.domain.model.OpportunityOutcome;
import io.github.archetom.crm.domain.model.OpportunityStatus;
import io.github.archetom.crm.domain.policy.OpportunityTemporalValuePolicy;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OpportunityId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Tenant-owned opportunity aggregate and its atomic relationship collections. */
@Getter
public class Opportunity extends AggregateRoot<OpportunityId> {

    public static final int MAX_CONTACTS = 100;
    public static final int MAX_COLLABORATORS = 100;
    public static final int MAX_CUSTOM_FIELDS = 200;
    public static final int MAX_PRODUCTS = 100;

    private OpportunityId id;
    private TenantId tenantId;
    private MemberId ownerMemberId;
    private CustomerId customerId;
    private ContactId primaryContactId;
    private String name;
    private String pipelineCode;
    private String stageCode;
    private LocalDate expectedCloseDate;
    private BigDecimal estimatedAmount;
    private LocalDateTime nextContactTime;
    private String description;
    private OpportunityOutcome outcome;
    private LocalDateTime outcomeTime;
    private String outcomeRemark;
    private OpportunityStatus status;
    private List<ContactId> contactIds = List.of();
    private List<OpportunityCollaborator> collaborators = List.of();
    private List<OpportunityFieldValue> customFieldValues = List.of();
    private List<OpportunityProductLine> productLines = List.of();
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private Opportunity() {
    }

    public record Snapshot(
            OpportunityId id,
            TenantId tenantId,
            MemberId ownerMemberId,
            CustomerId customerId,
            ContactId primaryContactId,
            String name,
            String pipelineCode,
            String stageCode,
            LocalDate expectedCloseDate,
            BigDecimal estimatedAmount,
            LocalDateTime nextContactTime,
            String description,
            OpportunityOutcome outcome,
            LocalDateTime outcomeTime,
            String outcomeRemark,
            OpportunityStatus status,
            List<ContactId> contactIds,
            List<OpportunityCollaborator> collaborators,
            List<OpportunityFieldValue> customFieldValues,
            List<OpportunityProductLine> productLines,
            Long version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
    }

    public static Opportunity create(
            TenantId tenantId,
            MemberId ownerMemberId,
            CustomerId customerId,
            String name,
            String pipelineCode,
            String stageCode,
            LocalDate expectedCloseDate,
            BigDecimal estimatedAmount,
            LocalDateTime nextContactTime,
            String description,
            Collection<ContactId> contactIds,
            ContactId primaryContactId,
            Collection<OpportunityCollaborator> collaborators,
            Collection<OpportunityFieldValue> customFieldValues) {
        Opportunity opportunity = new Opportunity();
        opportunity.tenantId = requireTenant(tenantId);
        opportunity.ownerMemberId = requireOwner(ownerMemberId, OpportunityStatus.ACTIVE);
        opportunity.customerId = requireCustomer(customerId);
        opportunity.status = OpportunityStatus.ACTIVE;
        opportunity.outcome = OpportunityOutcome.OPEN;
        opportunity.applyDetails(name, pipelineCode, stageCode, expectedCloseDate,
                estimatedAmount, nextContactTime, description, customFieldValues);
        opportunity.applyContacts(contactIds, primaryContactId);
        opportunity.collaborators = validateCollaborators(
                collaborators, opportunity.ownerMemberId);
        opportunity.createdTime = opportunity.updatedTime;
        return opportunity;
    }

    public static Opportunity reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new OpportunityDomainException("opportunity snapshot must not be empty");
        }
        Opportunity opportunity = new Opportunity();
        opportunity.id = snapshot.id();
        opportunity.tenantId = requireTenant(snapshot.tenantId());
        opportunity.status = snapshot.status() == null
                ? OpportunityStatus.DELETED : snapshot.status();
        opportunity.ownerMemberId = requireOwner(snapshot.ownerMemberId(), opportunity.status);
        opportunity.customerId = requireCustomer(snapshot.customerId());
        opportunity.name = requireName(snapshot.name());
        opportunity.pipelineCode = requireCode(snapshot.pipelineCode(), "pipeline");
        opportunity.stageCode = requireCode(snapshot.stageCode(), "stage");
        opportunity.expectedCloseDate = OpportunityTemporalValuePolicy.requireDate(
                snapshot.expectedCloseDate(), "opportunity expected close date");
        opportunity.estimatedAmount = requireAmount(snapshot.estimatedAmount());
        opportunity.nextContactTime = OpportunityTemporalValuePolicy.normalizeDateTime(
                snapshot.nextContactTime(), "opportunity next contact time");
        opportunity.description = optional(snapshot.description(), 2000,
                "opportunity description");
        opportunity.outcome = snapshot.outcome() == null
                ? OpportunityOutcome.OPEN : snapshot.outcome();
        opportunity.outcomeTime = OpportunityTemporalValuePolicy.normalizeDateTime(
                snapshot.outcomeTime(), "opportunity outcome time");
        opportunity.outcomeRemark = optional(snapshot.outcomeRemark(), 1000,
                "opportunity outcome remark");
        validateOutcome(opportunity.outcome, opportunity.outcomeTime,
                opportunity.outcomeRemark);
        opportunity.applyContacts(snapshot.contactIds(), snapshot.primaryContactId());
        opportunity.collaborators = validateCollaborators(
                snapshot.collaborators(), opportunity.ownerMemberId);
        opportunity.customFieldValues = validateCustomFields(snapshot.customFieldValues());
        opportunity.productLines = validateProductLines(snapshot.productLines());
        opportunity.restoreVersion(snapshot.version());
        opportunity.createdTime = OpportunityTemporalValuePolicy.normalizeDateTime(
                snapshot.createdTime(), "opportunity created time");
        opportunity.updatedTime = OpportunityTemporalValuePolicy.normalizeDateTime(
                snapshot.updatedTime(), "opportunity updated time");
        return opportunity;
    }

    public void replaceDetails(
            String name,
            String pipelineCode,
            String stageCode,
            LocalDate expectedCloseDate,
            BigDecimal estimatedAmount,
            LocalDateTime nextContactTime,
            String description,
            Collection<OpportunityFieldValue> customFieldValues) {
        ensureEditable();
        applyDetails(name, pipelineCode, stageCode, expectedCloseDate,
                estimatedAmount, nextContactTime, description, customFieldValues);
    }

    public void changeCustomer(CustomerId customerId) {
        ensureEditable();
        this.customerId = requireCustomer(customerId);
        touch();
    }

    public void changeOwner(MemberId ownerMemberId) {
        ensureEditable();
        MemberId nextOwner = requireOwner(ownerMemberId, status);
        collaborators = collaborators.stream()
                .filter(value -> !value.memberId().sameValueAs(nextOwner))
                .toList();
        this.ownerMemberId = nextOwner;
        touch();
    }

    public void changeStage(String pipelineCode, String stageCode) {
        ensureEditable();
        this.pipelineCode = requireCode(pipelineCode, "pipeline");
        this.stageCode = requireCode(stageCode, "stage");
        touch();
    }

    public void changeOutcome(OpportunityOutcome outcome, String remark) {
        ensureActive();
        OpportunityOutcome next = outcome == null ? OpportunityOutcome.OPEN : outcome;
        String nextRemark = optional(remark, 1000, "opportunity outcome remark");
        if (next == OpportunityOutcome.OPEN) {
            outcomeTime = null;
            outcomeRemark = null;
        } else {
            outcomeTime = OpportunityTemporalValuePolicy.normalizeDateTime(
                    LocalDateTime.now(), "opportunity outcome time");
            outcomeRemark = nextRemark;
        }
        this.outcome = next;
        touch();
    }

    public void replaceContacts(
            Collection<ContactId> contactIds, ContactId primaryContactId) {
        ensureEditable();
        applyContacts(contactIds, primaryContactId);
        touch();
    }

    public void changePrimaryContact(ContactId primaryContactId) {
        ensureEditable();
        if (primaryContactId != null && contactIds.stream()
                .noneMatch(value -> value.sameValueAs(primaryContactId))) {
            throw new OpportunityDomainException(
                    "primary contact must be related to the opportunity");
        }
        this.primaryContactId = primaryContactId;
        touch();
    }

    public void replaceCollaborators(
            Collection<OpportunityCollaborator> collaborators) {
        ensureEditable();
        this.collaborators = validateCollaborators(collaborators, ownerMemberId);
        touch();
    }

    public void replaceProductLines(Collection<OpportunityProductLine> productLines) {
        ensureEditable();
        this.productLines = validateProductLines(productLines);
        touch();
    }

    public boolean exitCollaborator(MemberId memberId) {
        ensureEditable();
        if (memberId == null || ownerMemberId.sameValueAs(memberId)) {
            throw new OpportunityDomainException(
                    "opportunity owner cannot exit as a collaborator");
        }
        List<OpportunityCollaborator> remaining = collaborators.stream()
                .filter(value -> !value.memberId().sameValueAs(memberId))
                .toList();
        if (remaining.size() == collaborators.size()) {
            return false;
        }
        collaborators = remaining;
        touch();
        return true;
    }

    public void delete() {
        ensureActive();
        status = OpportunityStatus.DELETED;
        touch();
    }

    public boolean isActive() {
        return status == OpportunityStatus.ACTIVE;
    }

    public boolean isOpen() {
        return isActive() && outcome == OpportunityOutcome.OPEN;
    }

    public void onPersisted(
            OpportunityId id, Long version,
            LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new OpportunityDomainException("persisted opportunity ID is invalid");
        }
        this.id = id;
        restoreVersion(version);
        if (createdTime != null) {
            this.createdTime = OpportunityTemporalValuePolicy.normalizeDateTime(
                    createdTime, "opportunity created time");
        }
        if (updatedTime != null) {
            this.updatedTime = OpportunityTemporalValuePolicy.normalizeDateTime(
                    updatedTime, "opportunity updated time");
        }
    }

    private void applyDetails(
            String name,
            String pipelineCode,
            String stageCode,
            LocalDate expectedCloseDate,
            BigDecimal estimatedAmount,
            LocalDateTime nextContactTime,
            String description,
            Collection<OpportunityFieldValue> customFieldValues) {
        String nextName = requireName(name);
        String nextPipelineCode = requireCode(pipelineCode, "pipeline");
        String nextStageCode = requireCode(stageCode, "stage");
        LocalDate nextExpectedCloseDate = OpportunityTemporalValuePolicy.requireDate(
                expectedCloseDate, "opportunity expected close date");
        BigDecimal nextEstimatedAmount = requireAmount(estimatedAmount);
        LocalDateTime nextContactTimeValue = OpportunityTemporalValuePolicy.normalizeDateTime(
                nextContactTime, "opportunity next contact time");
        String nextDescription = optional(
                description, 2000, "opportunity description");
        List<OpportunityFieldValue> nextCustomFieldValues =
                validateCustomFields(customFieldValues);

        this.name = nextName;
        this.pipelineCode = nextPipelineCode;
        this.stageCode = nextStageCode;
        this.expectedCloseDate = nextExpectedCloseDate;
        this.estimatedAmount = nextEstimatedAmount;
        this.nextContactTime = nextContactTimeValue;
        this.description = nextDescription;
        this.customFieldValues = nextCustomFieldValues;
        touch();
    }

    private void applyContacts(
            Collection<ContactId> values, ContactId primaryContactId) {
        List<ContactId> next = validateContacts(values);
        if (primaryContactId != null && next.stream()
                .noneMatch(value -> value.sameValueAs(primaryContactId))) {
            throw new OpportunityDomainException(
                    "primary contact must be related to the opportunity");
        }
        this.contactIds = next;
        this.primaryContactId = primaryContactId;
    }

    private void ensureEditable() {
        ensureActive();
        if (outcome != OpportunityOutcome.OPEN) {
            throw new OpportunityDomainException(
                    "closed opportunities must be reopened before editing");
        }
    }

    private void ensureActive() {
        if (status != OpportunityStatus.ACTIVE) {
            throw new OpportunityDomainException("only active opportunities can change");
        }
    }

    private void touch() {
        updatedTime = OpportunityTemporalValuePolicy.normalizeDateTime(
                LocalDateTime.now(), "opportunity updated time");
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new OpportunityDomainException("opportunity tenant must not be empty");
        }
        return value;
    }

    private static MemberId requireOwner(
            MemberId value, OpportunityStatus status) {
        if (value == null && status == OpportunityStatus.ACTIVE) {
            throw new OpportunityDomainException(
                    "active opportunity owner must not be empty");
        }
        return value;
    }

    private static CustomerId requireCustomer(CustomerId value) {
        if (value == null) {
            throw new OpportunityDomainException(
                    "opportunity customer must not be empty");
        }
        return value;
    }

    private static String requireName(String value) {
        String normalized = optional(value, 200, "opportunity name");
        if (normalized == null) {
            throw new OpportunityDomainException(
                    "opportunity name must not be empty");
        }
        return normalized;
    }

    private static String requireCode(String value, String description) {
        String normalized = optional(value, 64, "opportunity " + description + " code");
        if (normalized == null || !normalized.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new OpportunityDomainException(
                    "opportunity " + description + " code is invalid");
        }
        return normalized;
    }

    private static BigDecimal requireAmount(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal normalized;
        try {
            normalized = value.setScale(2);
        } catch (ArithmeticException exception) {
            throw new OpportunityDomainException(
                    "opportunity amount supports at most two decimal places");
        }
        if (normalized.signum() < 0 || normalized.precision() > 18) {
            throw new OpportunityDomainException("opportunity amount is invalid");
        }
        return normalized;
    }

    private static String optional(String value, int max, String description) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new OpportunityDomainException(description + " is too long");
        }
        return normalized;
    }

    private static void validateOutcome(
            OpportunityOutcome outcome, LocalDateTime time, String remark) {
        if (outcome == OpportunityOutcome.OPEN && (time != null || remark != null)) {
            throw new OpportunityDomainException(
                    "open opportunity cannot keep a closing result");
        }
        if (outcome != OpportunityOutcome.OPEN && time == null) {
            throw new OpportunityDomainException(
                    "closed opportunity must keep its outcome time");
        }
    }

    private static List<ContactId> validateContacts(Collection<ContactId> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_CONTACTS) {
            throw new OpportunityDomainException(
                    "opportunity contacts exceed the supported limit");
        }
        Set<ContactId> unique = new LinkedHashSet<>();
        for (ContactId value : values) {
            if (value == null || !unique.add(value)) {
                throw new OpportunityDomainException("opportunity contacts are invalid");
            }
        }
        return List.copyOf(unique);
    }

    private static List<OpportunityCollaborator> validateCollaborators(
            Collection<OpportunityCollaborator> values, MemberId ownerMemberId) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_COLLABORATORS) {
            throw new OpportunityDomainException(
                    "opportunity collaborators exceed the supported limit");
        }
        LinkedHashMap<MemberId, OpportunityCollaborator> unique = new LinkedHashMap<>();
        for (OpportunityCollaborator value : values) {
            if (value == null || ownerMemberId != null
                    && ownerMemberId.sameValueAs(value.memberId())
                    || unique.putIfAbsent(value.memberId(), value) != null) {
                throw new OpportunityDomainException(
                        "opportunity collaborators are invalid");
            }
        }
        return List.copyOf(unique.values());
    }

    private static List<OpportunityFieldValue> validateCustomFields(
            Collection<OpportunityFieldValue> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_CUSTOM_FIELDS) {
            throw new OpportunityDomainException(
                    "opportunity custom fields exceed the supported limit");
        }
        Set<FieldCode> unique = new LinkedHashSet<>();
        for (OpportunityFieldValue value : values) {
            if (value == null || !unique.add(value.fieldCode())) {
                throw new OpportunityDomainException(
                        "opportunity custom field codes must be unique");
            }
        }
        return List.copyOf(values);
    }

    private static List<OpportunityProductLine> validateProductLines(
            Collection<OpportunityProductLine> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_PRODUCTS) {
            throw new OpportunityDomainException(
                    "opportunity products exceed the supported limit");
        }
        Set<io.github.archetom.crm.domain.valueobject.ProductId> products =
                new LinkedHashSet<>();
        Set<Integer> orders = new LinkedHashSet<>();
        for (OpportunityProductLine value : values) {
            if (value == null || !products.add(value.productId())
                    || !orders.add(value.displayOrder())) {
                throw new OpportunityDomainException(
                        "opportunity product lines are invalid");
            }
        }
        return values.stream().sorted(java.util.Comparator.comparingInt(
                OpportunityProductLine::displayOrder)).toList();
    }
}
