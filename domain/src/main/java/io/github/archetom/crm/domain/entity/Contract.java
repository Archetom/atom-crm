package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.ContractDomainException;
import io.github.archetom.crm.domain.model.ContractStatus;
import io.github.archetom.crm.domain.policy.IdentifierKeyNormalizer;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OpportunityId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

@Getter
public class Contract extends AggregateRoot<ContractId> {

    public static final int MAX_PRODUCTS = 100;
    public static final int MAX_COLLABORATORS = 100;
    public static final int MAX_CUSTOM_FIELDS = 200;
    private static final LocalDate MINIMUM_DATE = LocalDate.of(1000, 1, 1);
    private static final LocalDate MAXIMUM_DATE = LocalDate.of(9999, 12, 31);

    private ContractId id;
    private TenantId tenantId;
    private MemberId ownerMemberId;
    private CustomerId customerId;
    private OpportunityId opportunityId;
    private ContactId contactId;
    private String name;
    private String number;
    private String numberKey;
    private LocalDate orderDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private String contractType;
    private String paymentType;
    private String remark;
    private BigDecimal subtotal;
    private BigDecimal discountRate;
    private BigDecimal totalAmount;
    private ContractStatus status;
    private List<ContractProductLine> productLines = List.of();
    private List<ContractCollaborator> collaborators = List.of();
    private List<ContractFieldValue> customFieldValues = List.of();
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private Contract() {
    }

    public record Snapshot(
            ContractId id, TenantId tenantId, MemberId ownerMemberId,
            CustomerId customerId, OpportunityId opportunityId, ContactId contactId,
            String name, String number, String numberKey, LocalDate orderDate,
            LocalDate startDate, LocalDate endDate, String contractType,
            String paymentType, String remark, BigDecimal subtotal,
            BigDecimal discountRate, BigDecimal totalAmount, ContractStatus status,
            List<ContractProductLine> productLines,
            List<ContractCollaborator> collaborators,
            List<ContractFieldValue> customFieldValues,
            Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {
    }

    public static Contract create(
            TenantId tenantId, MemberId ownerMemberId, CustomerId customerId,
            OpportunityId opportunityId, ContactId contactId, String name, String number,
            LocalDate orderDate, LocalDate startDate, LocalDate endDate,
            String contractType, String paymentType, String remark,
            BigDecimal discountRate, Collection<ContractProductLine> productLines,
            Collection<ContractCollaborator> collaborators,
            Collection<ContractFieldValue> customFieldValues) {
        Contract contract = new Contract();
        contract.tenantId = requireTenant(tenantId);
        contract.ownerMemberId = requireOwner(ownerMemberId);
        contract.customerId = requireCustomer(customerId);
        contract.status = ContractStatus.ACTIVE;
        contract.applyDetails(customerId, opportunityId, contactId, name, number,
                orderDate, startDate, endDate, contractType, paymentType, remark,
                discountRate, productLines, customFieldValues);
        contract.collaborators = collaborators(collaborators, ownerMemberId);
        contract.createdTime = contract.updatedTime;
        return contract;
    }

    public static Contract reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new ContractDomainException("contract snapshot must not be empty");
        }
        Contract contract = new Contract();
        contract.id = snapshot.id();
        contract.tenantId = requireTenant(snapshot.tenantId());
        contract.ownerMemberId = requireOwner(snapshot.ownerMemberId());
        contract.customerId = requireCustomer(snapshot.customerId());
        contract.opportunityId = snapshot.opportunityId();
        contract.contactId = snapshot.contactId();
        contract.name = required(snapshot.name(), 200, "contract name");
        contract.number = required(snapshot.number(), 100, "contract number");
        contract.numberKey = normalizeNumber(snapshot.numberKey() == null
                ? snapshot.number() : snapshot.numberKey());
        validateDates(snapshot.orderDate(), snapshot.startDate(), snapshot.endDate());
        contract.orderDate = snapshot.orderDate();
        contract.startDate = snapshot.startDate();
        contract.endDate = snapshot.endDate();
        contract.contractType = optional(snapshot.contractType(), 100, "contract type");
        contract.paymentType = optional(snapshot.paymentType(), 200, "payment type");
        contract.remark = optional(snapshot.remark(), 2000, "contract remark");
        contract.productLines = productLines(snapshot.productLines());
        contract.discountRate = rate(snapshot.discountRate());
        contract.subtotal = money(snapshot.subtotal(), "contract subtotal");
        contract.totalAmount = money(snapshot.totalAmount(), "contract total amount");
        contract.requireCalculatedAmounts();
        contract.status = snapshot.status() == null ? ContractStatus.DELETED : snapshot.status();
        contract.collaborators = collaborators(snapshot.collaborators(), contract.ownerMemberId);
        contract.customFieldValues = customFields(snapshot.customFieldValues());
        contract.restoreVersion(snapshot.version());
        contract.createdTime = snapshot.createdTime();
        contract.updatedTime = snapshot.updatedTime();
        return contract;
    }

    public void replaceDetails(
            CustomerId customerId, OpportunityId opportunityId, ContactId contactId,
            String name, String number, LocalDate orderDate, LocalDate startDate,
            LocalDate endDate, String contractType, String paymentType, String remark,
            BigDecimal discountRate, Collection<ContractProductLine> productLines,
            Collection<ContractFieldValue> customFieldValues) {
        ensureEditable();
        applyDetails(customerId, opportunityId, contactId, name, number, orderDate,
                startDate, endDate, contractType, paymentType, remark, discountRate,
                productLines, customFieldValues);
    }

    /**
     * Replaces only the product relation while preserving the contract's other
     * business fields and its existing contract-level discount rate.
     */
    public void replaceProductLines(Collection<ContractProductLine> values) {
        ensureEditable();
        productLines = productLines(values);
        subtotal = money(productLines.stream().map(ContractProductLine::lineAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add),
                "contract subtotal");
        totalAmount = money(subtotal
                .multiply(new BigDecimal("100").subtract(discountRate))
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP),
                "contract total amount");
        touch();
    }

    public void changeOwner(MemberId ownerMemberId) {
        ensureEditable();
        MemberId next = requireOwner(ownerMemberId);
        collaborators = collaborators.stream()
                .filter(value -> !value.memberId().sameValueAs(next)).toList();
        this.ownerMemberId = next;
        touch();
    }

    public void replaceCollaborators(Collection<ContractCollaborator> values) {
        ensureEditable();
        collaborators = collaborators(values, ownerMemberId);
        touch();
    }

    public boolean exitCollaborator(MemberId memberId) {
        ensureEditable();
        if (memberId == null || ownerMemberId.sameValueAs(memberId)) {
            throw new ContractDomainException("contract owner cannot exit collaboration");
        }
        List<ContractCollaborator> next = collaborators.stream()
                .filter(value -> !value.memberId().sameValueAs(memberId)).toList();
        if (next.size() == collaborators.size()) {
            return false;
        }
        collaborators = next;
        touch();
        return true;
    }

    public void voidContract() {
        ensureEditable();
        status = ContractStatus.VOIDED;
        touch();
    }

    public void delete() {
        ensureEditable();
        status = ContractStatus.DELETED;
        touch();
    }

    public boolean isActive() {
        return status == ContractStatus.ACTIVE;
    }

    public void onPersisted(
            ContractId id, Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new ContractDomainException("persisted contract ID is invalid");
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
            CustomerId customerId, OpportunityId opportunityId, ContactId contactId,
            String name, String number, LocalDate orderDate, LocalDate startDate,
            LocalDate endDate, String contractType, String paymentType, String remark,
            BigDecimal discountRate, Collection<ContractProductLine> productLines,
            Collection<ContractFieldValue> customFieldValues) {
        validateDates(orderDate, startDate, endDate);
        this.customerId = requireCustomer(customerId);
        this.opportunityId = opportunityId;
        this.contactId = contactId;
        this.name = required(name, 200, "contract name");
        this.number = required(number, 100, "contract number");
        this.numberKey = normalizeNumber(this.number);
        this.orderDate = orderDate;
        this.startDate = startDate;
        this.endDate = endDate;
        this.contractType = optional(contractType, 100, "contract type");
        this.paymentType = optional(paymentType, 200, "payment type");
        this.remark = optional(remark, 2000, "contract remark");
        this.productLines = productLines(productLines);
        this.customFieldValues = customFields(customFieldValues);
        this.discountRate = rate(discountRate);
        this.subtotal = money(this.productLines.stream()
                .map(ContractProductLine::lineAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add),
                "contract subtotal");
        this.totalAmount = money(subtotal
                .multiply(new BigDecimal("100").subtract(this.discountRate))
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP),
                "contract total amount");
        touch();
    }

    private void requireCalculatedAmounts() {
        BigDecimal expectedSubtotal = productLines.stream().map(ContractProductLine::lineAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        BigDecimal expectedTotal = expectedSubtotal
                .multiply(new BigDecimal("100").subtract(discountRate))
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        if (subtotal.compareTo(expectedSubtotal) != 0 || totalAmount.compareTo(expectedTotal) != 0) {
            throw new ContractDomainException("contract amounts are inconsistent");
        }
    }

    private void ensureEditable() {
        if (status != ContractStatus.ACTIVE) {
            throw new ContractDomainException("only active contracts can be changed");
        }
    }

    private void touch() {
        updatedTime = LocalDateTime.now();
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new ContractDomainException("contract tenant is required");
        }
        return value;
    }

    private static MemberId requireOwner(MemberId value) {
        if (value == null) {
            throw new ContractDomainException("contract owner is required");
        }
        return value;
    }

    private static CustomerId requireCustomer(CustomerId value) {
        if (value == null) {
            throw new ContractDomainException("contract customer is required");
        }
        return value;
    }

    private static void validateDates(
            LocalDate orderDate, LocalDate startDate, LocalDate endDate) {
        requireSupportedDate(orderDate, "contract order date");
        requireSupportedDate(startDate, "contract start date");
        requireSupportedDate(endDate, "contract end date");
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ContractDomainException("contract start date must not be after end date");
        }
    }

    private static void requireSupportedDate(LocalDate value, String description) {
        if (value != null && (value.isBefore(MINIMUM_DATE) || value.isAfter(MAXIMUM_DATE))) {
            throw new ContractDomainException(description + " is outside the supported range");
        }
    }

    private static BigDecimal rate(BigDecimal value) {
        BigDecimal source = value == null ? BigDecimal.ZERO : value;
        if (source.signum() < 0 || source.compareTo(new BigDecimal("100")) > 0) {
            throw new ContractDomainException("contract discount rate is invalid");
        }
        try {
            return source.setScale(4);
        } catch (ArithmeticException exception) {
            throw new ContractDomainException("contract discount rate supports four decimal places");
        }
    }

    private static BigDecimal money(BigDecimal value, String description) {
        if (value == null || value.signum() < 0) {
            throw new ContractDomainException(description + " is invalid");
        }
        try {
            BigDecimal normalized = value.setScale(2);
            if (normalized.precision() > 22) {
                throw new ContractDomainException(description + " is invalid");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new ContractDomainException(description + " supports two decimal places");
        }
    }

    private static List<ContractProductLine> productLines(Collection<ContractProductLine> values) {
        List<ContractProductLine> result = values == null ? List.of() : List.copyOf(values);
        if (result.size() > MAX_PRODUCTS || result.stream().anyMatch(java.util.Objects::isNull)
                || result.stream().map(ContractProductLine::productId).distinct().count() != result.size()) {
            throw new ContractDomainException("contract product lines are invalid");
        }
        return result;
    }

    private static List<ContractCollaborator> collaborators(
            Collection<ContractCollaborator> values, MemberId owner) {
        LinkedHashMap<MemberId, ContractCollaborator> result = new LinkedHashMap<>();
        if (values != null) {
            for (ContractCollaborator value : values) {
                if (value == null || value.memberId().sameValueAs(owner)
                        || result.putIfAbsent(value.memberId(), value) != null) {
                    throw new ContractDomainException("contract collaborators are invalid");
                }
            }
        }
        if (result.size() > MAX_COLLABORATORS) {
            throw new ContractDomainException("contract collaborators exceed the limit");
        }
        return List.copyOf(result.values());
    }

    private static List<ContractFieldValue> customFields(Collection<ContractFieldValue> values) {
        LinkedHashMap<Object, ContractFieldValue> result = new LinkedHashMap<>();
        if (values != null) {
            for (ContractFieldValue value : values) {
                if (value == null || result.putIfAbsent(value.fieldCode(), value) != null) {
                    throw new ContractDomainException("contract custom fields are invalid");
                }
            }
        }
        if (result.size() > MAX_CUSTOM_FIELDS) {
            throw new ContractDomainException("contract custom fields exceed the limit");
        }
        return List.copyOf(result.values());
    }

    private static String normalizeNumber(String value) {
        String normalized = IdentifierKeyNormalizer.normalize(required(value, 100, "contract number"));
        if (normalized.length() > 100) {
            throw new ContractDomainException(
                    "contract number is too long after normalization");
        }
        return normalized;
    }

    private static String required(String value, int max, String description) {
        String normalized = optional(value, max, description);
        if (normalized == null) {
            throw new ContractDomainException(description + " is required");
        }
        return normalized;
    }

    private static String optional(String value, int max, String description) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new ContractDomainException(description + " is too long");
        }
        return normalized;
    }
}
