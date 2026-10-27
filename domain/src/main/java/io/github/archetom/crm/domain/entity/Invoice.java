package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.InvoiceDomainException;
import io.github.archetom.crm.domain.model.InvoiceStatus;
import io.github.archetom.crm.domain.model.InvoiceTitleType;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.InvoiceId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

@Getter
public class Invoice extends AggregateRoot<InvoiceId> {

    public static final int MAX_CUSTOM_FIELDS = 200;
    private static final LocalDate MINIMUM_DATE = LocalDate.of(1000, 1, 1);
    private static final LocalDate MAXIMUM_DATE = LocalDate.of(9999, 12, 31);

    private InvoiceId id;
    private TenantId tenantId;
    private MemberId ownerMemberId;
    private CustomerId customerId;
    private ContractId contractId;
    private String applicationNumber;
    private BigDecimal amount;
    private LocalDate requestedDate;
    private String invoiceType;
    private InvoiceTitleType titleType;
    private String title;
    private String taxNumber;
    private String bankName;
    private String bankAccount;
    private String registeredAddress;
    private String registeredPhone;
    private String contactName;
    private String contactPhone;
    private String shippingAddress;
    private String remark;
    private InvoiceStatus status;
    private String invoiceNumber;
    private LocalDate issuedDate;
    private String logisticsNumber;
    private List<InvoiceFieldValue> customFieldValues = List.of();
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private Invoice() {
    }

    public record Snapshot(
            InvoiceId id, TenantId tenantId, MemberId ownerMemberId,
            CustomerId customerId, ContractId contractId, String applicationNumber,
            BigDecimal amount, LocalDate requestedDate, String invoiceType,
            InvoiceTitleType titleType, String title, String taxNumber,
            String bankName, String bankAccount, String registeredAddress,
            String registeredPhone, String contactName, String contactPhone,
            String shippingAddress, String remark, InvoiceStatus status,
            String invoiceNumber, LocalDate issuedDate, String logisticsNumber,
            List<InvoiceFieldValue> customFieldValues, Long version,
            LocalDateTime createdTime, LocalDateTime updatedTime) {
    }

    public static Invoice create(
            TenantId tenantId, MemberId ownerMemberId, CustomerId customerId,
            ContractId contractId, String applicationNumber, BigDecimal amount,
            LocalDate requestedDate, String invoiceType, InvoiceTitleType titleType,
            String title, String taxNumber, String bankName, String bankAccount,
            String registeredAddress, String registeredPhone, String contactName,
            String contactPhone, String shippingAddress, String remark,
            Collection<InvoiceFieldValue> customFieldValues) {
        Invoice invoice = new Invoice();
        invoice.tenantId = requireTenant(tenantId);
        invoice.ownerMemberId = requireOwner(ownerMemberId);
        invoice.customerId = requireCustomer(customerId);
        invoice.contractId = requireContract(contractId);
        invoice.applicationNumber = required(applicationNumber, 100, "invoice application number");
        invoice.status = InvoiceStatus.PENDING;
        invoice.applyDetails(amount, requestedDate, invoiceType, titleType, title,
                taxNumber, bankName, bankAccount, registeredAddress, registeredPhone,
                contactName, contactPhone, shippingAddress, remark, customFieldValues);
        invoice.createdTime = invoice.updatedTime;
        return invoice;
    }

    public static Invoice reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new InvoiceDomainException("invoice snapshot must not be empty");
        }
        Invoice invoice = new Invoice();
        invoice.id = snapshot.id();
        invoice.tenantId = requireTenant(snapshot.tenantId());
        invoice.ownerMemberId = requireOwner(snapshot.ownerMemberId());
        invoice.customerId = requireCustomer(snapshot.customerId());
        invoice.contractId = requireContract(snapshot.contractId());
        invoice.applicationNumber = required(
                snapshot.applicationNumber(), 100, "invoice application number");
        invoice.status = snapshot.status() == null ? InvoiceStatus.DELETED : snapshot.status();
        invoice.amount = money(snapshot.amount());
        invoice.requestedDate = supportedDate(
                snapshot.requestedDate(), "invoice requested date");
        invoice.invoiceType = required(snapshot.invoiceType(), 50, "invoice type");
        invoice.applyTitle(snapshot.titleType(), snapshot.title(), snapshot.taxNumber(),
                snapshot.bankName(), snapshot.bankAccount(), snapshot.registeredAddress(),
                snapshot.registeredPhone());
        invoice.contactName = optional(snapshot.contactName(), 100, "invoice contact name");
        invoice.contactPhone = optional(snapshot.contactPhone(), 50, "invoice contact phone");
        invoice.shippingAddress = optional(
                snapshot.shippingAddress(), 500, "invoice shipping address");
        invoice.remark = optional(snapshot.remark(), 2000, "invoice remark");
        invoice.invoiceNumber = optional(snapshot.invoiceNumber(), 100, "invoice number");
        invoice.issuedDate = supportedDate(snapshot.issuedDate(), "invoice issued date");
        invoice.logisticsNumber = optional(
                snapshot.logisticsNumber(), 100, "invoice logistics number");
        invoice.customFieldValues = customFields(snapshot.customFieldValues());
        invoice.validateIssuedState();
        invoice.restoreVersion(snapshot.version());
        invoice.createdTime = snapshot.createdTime();
        invoice.updatedTime = snapshot.updatedTime();
        return invoice;
    }

    public void replaceDetails(
            BigDecimal amount, LocalDate requestedDate, String invoiceType,
            InvoiceTitleType titleType, String title, String taxNumber,
            String bankName, String bankAccount, String registeredAddress,
            String registeredPhone, String contactName, String contactPhone,
            String shippingAddress, String remark,
            Collection<InvoiceFieldValue> customFieldValues) {
        ensurePending();
        applyDetails(amount, requestedDate, invoiceType, titleType, title, taxNumber,
                bankName, bankAccount, registeredAddress, registeredPhone, contactName,
                contactPhone, shippingAddress, remark, customFieldValues);
    }

    public void changeOwner(MemberId ownerMemberId) {
        ensureNotDeleted();
        this.ownerMemberId = requireOwner(ownerMemberId);
        touch();
    }

    public void issue(String invoiceNumber, LocalDate issuedDate, String logisticsNumber) {
        ensurePending();
        this.invoiceNumber = required(invoiceNumber, 100, "invoice number");
        if (issuedDate == null) {
            throw new InvoiceDomainException("invoice issued date is required");
        }
        this.issuedDate = supportedDate(issuedDate, "invoice issued date");
        this.logisticsNumber = optional(logisticsNumber, 100, "invoice logistics number");
        this.status = InvoiceStatus.ISSUED;
        touch();
    }

    public void reset() {
        if (status != InvoiceStatus.ISSUED) {
            throw new InvoiceDomainException("only issued invoices can be reset");
        }
        status = InvoiceStatus.PENDING;
        invoiceNumber = null;
        issuedDate = null;
        logisticsNumber = null;
        touch();
    }

    public void delete() {
        ensurePending();
        status = InvoiceStatus.DELETED;
        touch();
    }

    public boolean isDeleted() {
        return status == InvoiceStatus.DELETED;
    }

    public void onPersisted(
            InvoiceId id, Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new InvoiceDomainException("persisted invoice ID is invalid");
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
            BigDecimal amount, LocalDate requestedDate, String invoiceType,
            InvoiceTitleType titleType, String title, String taxNumber,
            String bankName, String bankAccount, String registeredAddress,
            String registeredPhone, String contactName, String contactPhone,
            String shippingAddress, String remark,
            Collection<InvoiceFieldValue> customFieldValues) {
        this.amount = money(amount);
        this.requestedDate = supportedDate(requestedDate, "invoice requested date");
        this.invoiceType = required(invoiceType, 50, "invoice type");
        applyTitle(titleType, title, taxNumber, bankName, bankAccount,
                registeredAddress, registeredPhone);
        this.contactName = optional(contactName, 100, "invoice contact name");
        this.contactPhone = optional(contactPhone, 50, "invoice contact phone");
        this.shippingAddress = optional(shippingAddress, 500, "invoice shipping address");
        this.remark = optional(remark, 2000, "invoice remark");
        this.customFieldValues = customFields(customFieldValues);
        touch();
    }

    private void applyTitle(
            InvoiceTitleType titleType, String title, String taxNumber,
            String bankName, String bankAccount, String registeredAddress,
            String registeredPhone) {
        if (titleType == null) {
            throw new InvoiceDomainException("invoice title type is required");
        }
        this.titleType = titleType;
        this.title = required(title, 200, "invoice title");
        if (titleType == InvoiceTitleType.INDIVIDUAL) {
            this.taxNumber = null;
            this.bankName = null;
            this.bankAccount = null;
            this.registeredAddress = null;
            this.registeredPhone = null;
            return;
        }
        this.taxNumber = required(taxNumber, 100, "invoice tax number");
        this.bankName = optional(bankName, 200, "invoice bank name");
        this.bankAccount = optional(bankAccount, 100, "invoice bank account");
        this.registeredAddress = optional(
                registeredAddress, 500, "invoice registered address");
        this.registeredPhone = optional(
                registeredPhone, 50, "invoice registered phone");
    }

    private void validateIssuedState() {
        if (status == InvoiceStatus.ISSUED
                && (invoiceNumber == null || issuedDate == null)) {
            throw new InvoiceDomainException("issued invoice result is incomplete");
        }
        if (status == InvoiceStatus.PENDING
                && (invoiceNumber != null || issuedDate != null || logisticsNumber != null)) {
            throw new InvoiceDomainException("pending invoice must not contain an issue result");
        }
    }

    private void ensurePending() {
        if (status != InvoiceStatus.PENDING) {
            throw new InvoiceDomainException("only pending invoices can be changed");
        }
    }

    private void ensureNotDeleted() {
        if (status == InvoiceStatus.DELETED) {
            throw new InvoiceDomainException("deleted invoices cannot be changed");
        }
    }

    private void touch() {
        updatedTime = LocalDateTime.now();
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new InvoiceDomainException("invoice tenant is required");
        }
        return value;
    }

    private static MemberId requireOwner(MemberId value) {
        if (value == null) {
            throw new InvoiceDomainException("invoice owner is required");
        }
        return value;
    }

    private static CustomerId requireCustomer(CustomerId value) {
        if (value == null) {
            throw new InvoiceDomainException("invoice customer is required");
        }
        return value;
    }

    private static ContractId requireContract(ContractId value) {
        if (value == null) {
            throw new InvoiceDomainException("invoice contract is required");
        }
        return value;
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new InvoiceDomainException("invoice amount is invalid");
        }
        try {
            BigDecimal normalized = value.setScale(2);
            if (normalized.precision() > 22) {
                throw new InvoiceDomainException("invoice amount is invalid");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new InvoiceDomainException("invoice amount supports two decimal places");
        }
    }

    private static LocalDate supportedDate(LocalDate value, String description) {
        if (value != null && (value.isBefore(MINIMUM_DATE) || value.isAfter(MAXIMUM_DATE))) {
            throw new InvoiceDomainException(description + " is outside the supported range");
        }
        return value;
    }

    private static List<InvoiceFieldValue> customFields(
            Collection<InvoiceFieldValue> values) {
        LinkedHashMap<Object, InvoiceFieldValue> result = new LinkedHashMap<>();
        if (values != null) {
            for (InvoiceFieldValue value : values) {
                if (value == null || result.putIfAbsent(value.fieldCode(), value) != null) {
                    throw new InvoiceDomainException("invoice custom fields are invalid");
                }
            }
        }
        if (result.size() > MAX_CUSTOM_FIELDS) {
            throw new InvoiceDomainException("invoice custom fields exceed the limit");
        }
        return List.copyOf(result.values());
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
