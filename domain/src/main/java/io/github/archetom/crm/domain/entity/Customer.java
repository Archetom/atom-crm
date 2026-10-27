package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.CustomerDomainException;
import io.github.archetom.crm.domain.model.CustomerDealStatus;
import io.github.archetom.crm.domain.model.CustomerStatus;
import io.github.archetom.crm.domain.policy.CustomerTagPolicy;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Tenant-owned customer aggregate and its bounded child values. */
@Getter
public class Customer extends AggregateRoot<CustomerId> {

    public static final int MAX_TAGS = 50;
    public static final int MAX_ADDRESSES = 20;
    public static final int MAX_COLLABORATORS = 100;
    public static final int MAX_CUSTOM_FIELDS = 200;
    private static final int MAX_WEBSITE_LENGTH = 500;

    private CustomerId id;
    private TenantId tenantId;
    private MemberId ownerMemberId;
    private CustomerId parentCustomerId;
    private String name;
    private String phoneNumber;
    private String telephone;
    private String email;
    private String website;
    private String description;
    private CustomerStatus status;
    private boolean locked;
    private CustomerDealStatus dealStatus;
    private LocalDateTime dealTime;
    private List<String> tags = List.of();
    private List<CustomerAddress> addresses = List.of();
    private List<CustomerCollaborator> collaborators = List.of();
    private List<CustomerFieldValue> customFieldValues = List.of();
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private Customer() {
    }

    public record Snapshot(
            CustomerId id,
            TenantId tenantId,
            MemberId ownerMemberId,
            CustomerId parentCustomerId,
            String name,
            String phoneNumber,
            String telephone,
            String email,
            String website,
            String description,
            CustomerStatus status,
            boolean locked,
            CustomerDealStatus dealStatus,
            LocalDateTime dealTime,
            List<String> tags,
            List<CustomerAddress> addresses,
            List<CustomerCollaborator> collaborators,
            List<CustomerFieldValue> customFieldValues,
            Long version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
    }

    public static Customer create(
            TenantId tenantId,
            MemberId ownerMemberId,
            String name,
            String phoneNumber,
            String telephone,
            String email,
            String website,
            String description,
            Collection<CustomerAddress> addresses,
            Collection<CustomerFieldValue> customFieldValues) {
        Customer customer = new Customer();
        customer.tenantId = requireTenant(tenantId);
        customer.ownerMemberId = requireOwner(ownerMemberId, CustomerStatus.ACTIVE);
        customer.status = CustomerStatus.ACTIVE;
        customer.locked = false;
        customer.dealStatus = CustomerDealStatus.OPEN;
        customer.applyDetails(name, phoneNumber, telephone, email, website, description,
                addresses, customFieldValues);
        customer.createdTime = customer.updatedTime;
        return customer;
    }

    public static Customer reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new CustomerDomainException("customer snapshot must not be empty");
        }
        Customer customer = new Customer();
        customer.id = snapshot.id();
        customer.tenantId = requireTenant(snapshot.tenantId());
        customer.status = snapshot.status() == null ? CustomerStatus.DELETED : snapshot.status();
        customer.ownerMemberId = requireOwner(snapshot.ownerMemberId(), customer.status);
        customer.parentCustomerId = snapshot.parentCustomerId();
        customer.name = requireName(snapshot.name());
        customer.phoneNumber = optional(snapshot.phoneNumber(), 30, "customer phone number");
        customer.telephone = optional(snapshot.telephone(), 50, "customer telephone");
        customer.email = requireEmail(snapshot.email());
        customer.website = requireWebsite(snapshot.website());
        customer.description = optional(snapshot.description(), 2000, "customer description");
        customer.locked = snapshot.locked();
        customer.dealStatus = snapshot.dealStatus() == null ? CustomerDealStatus.OPEN : snapshot.dealStatus();
        customer.dealTime = snapshot.dealTime();
        customer.tags = validateTags(snapshot.tags());
        customer.addresses = validateAddresses(snapshot.addresses());
        customer.collaborators = validateCollaborators(
                snapshot.collaborators(), customer.ownerMemberId);
        customer.customFieldValues = validateCustomFields(snapshot.customFieldValues());
        customer.restoreVersion(snapshot.version());
        customer.createdTime = snapshot.createdTime();
        customer.updatedTime = snapshot.updatedTime();
        return customer;
    }

    public void replaceDetails(
            String name,
            String phoneNumber,
            String telephone,
            String email,
            String website,
            String description,
            Collection<CustomerAddress> addresses,
            Collection<CustomerFieldValue> customFieldValues) {
        ensureMutable();
        applyDetails(name, phoneNumber, telephone, email, website, description,
                addresses, customFieldValues);
    }

    public void changeState(boolean locked, CustomerDealStatus dealStatus) {
        ensureMutable();
        CustomerDealStatus nextDealStatus = dealStatus == null ? CustomerDealStatus.OPEN : dealStatus;
        if (this.dealStatus != CustomerDealStatus.WON && nextDealStatus == CustomerDealStatus.WON) {
            dealTime = LocalDateTime.now();
        } else if (this.dealStatus == CustomerDealStatus.WON && nextDealStatus != CustomerDealStatus.WON) {
            dealTime = null;
        }
        this.locked = locked;
        this.dealStatus = nextDealStatus;
        touch();
    }

    public void changeOwner(MemberId ownerMemberId) {
        ensureMutable();
        MemberId nextOwner = requireOwner(ownerMemberId, status);
        collaborators = collaborators.stream()
                .filter(value -> !value.memberId().sameValueAs(nextOwner))
                .toList();
        this.ownerMemberId = nextOwner;
        touch();
    }

    public void changeParent(CustomerId parentCustomerId) {
        ensureMutable();
        if (id != null && id.sameValueAs(parentCustomerId)) {
            throw new CustomerDomainException("a customer cannot be its own parent");
        }
        this.parentCustomerId = parentCustomerId;
        touch();
    }

    public void replaceTags(Collection<String> tags) {
        ensureMutable();
        this.tags = validateTags(tags);
        touch();
    }

    public void changeTags(Collection<String> addedTags, Collection<String> removedTags) {
        ensureMutable();
        Map<String, String> values = new LinkedHashMap<>();
        for (String tag : tags) {
            values.put(CustomerTagPolicy.key(tag), tag);
        }
        for (String tag : validateTags(addedTags)) {
            values.putIfAbsent(CustomerTagPolicy.key(tag), tag);
        }
        for (String tag : validateTags(removedTags)) {
            values.remove(CustomerTagPolicy.key(tag));
        }
        this.tags = validateTags(values.values());
        touch();
    }

    public void replaceCollaborators(Collection<CustomerCollaborator> collaborators) {
        ensureMutable();
        this.collaborators = validateCollaborators(collaborators, ownerMemberId);
        touch();
    }

    public boolean exitCollaborator(MemberId memberId) {
        ensureMutable();
        if (memberId == null || ownerMemberId.sameValueAs(memberId)) {
            throw new CustomerDomainException("customer owner cannot exit as a collaborator");
        }
        List<CustomerCollaborator> remaining = collaborators.stream()
                .filter(value -> !value.memberId().sameValueAs(memberId))
                .toList();
        if (remaining.size() == collaborators.size()) {
            return false;
        }
        this.collaborators = remaining;
        touch();
        return true;
    }

    public void delete() {
        ensureMutable();
        status = CustomerStatus.DELETED;
        touch();
    }

    /** Removes ownership when a visible active customer enters a tenant pool. */
    public void enterPool() {
        ensureMutable();
        ownerMemberId = null;
        collaborators = List.of();
        status = CustomerStatus.IN_POOL;
        touch();
    }

    /** Restores ownership when a pool customer is claimed or assigned. */
    public void leavePool(MemberId ownerMemberId) {
        if (status != CustomerStatus.IN_POOL) {
            throw new CustomerDomainException("only pool customers can leave a pool");
        }
        this.ownerMemberId = requireOwner(ownerMemberId, CustomerStatus.ACTIVE);
        status = CustomerStatus.ACTIVE;
        touch();
    }

    /** Soft-deletes a pool customer without making it active first. */
    public void deleteFromPool() {
        if (status != CustomerStatus.IN_POOL) {
            throw new CustomerDomainException("only pool customers can be deleted from a pool");
        }
        status = CustomerStatus.DELETED;
        touch();
    }

    public boolean isActive() {
        return status == CustomerStatus.ACTIVE;
    }

    public boolean isDeleted() {
        return status == CustomerStatus.DELETED;
    }

    public void onPersisted(CustomerId id, Long version,
                            LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new CustomerDomainException("persisted customer ID is invalid");
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
            String name,
            String phoneNumber,
            String telephone,
            String email,
            String website,
            String description,
            Collection<CustomerAddress> addresses,
            Collection<CustomerFieldValue> customFieldValues) {
        String nextName = requireName(name);
        String nextPhoneNumber = optional(phoneNumber, 30, "customer phone number");
        String nextTelephone = optional(telephone, 50, "customer telephone");
        String nextEmail = requireEmail(email);
        String nextWebsite = requireWebsite(website);
        String nextDescription = optional(description, 2000, "customer description");
        List<CustomerAddress> nextAddresses = validateAddresses(addresses);
        List<CustomerFieldValue> nextCustomFields = validateCustomFields(customFieldValues);

        this.name = nextName;
        this.phoneNumber = nextPhoneNumber;
        this.telephone = nextTelephone;
        this.email = nextEmail;
        this.website = nextWebsite;
        this.description = nextDescription;
        this.addresses = nextAddresses;
        this.customFieldValues = nextCustomFields;
        touch();
    }

    private void ensureMutable() {
        if (status != CustomerStatus.ACTIVE) {
            throw new CustomerDomainException("only active customers can change");
        }
    }

    private void touch() {
        updatedTime = LocalDateTime.now();
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new CustomerDomainException("customer tenant must not be empty");
        }
        return value;
    }

    private static MemberId requireOwner(MemberId value, CustomerStatus status) {
        if (value == null && status == CustomerStatus.ACTIVE) {
            throw new CustomerDomainException("active customer owner must not be empty");
        }
        return value;
    }

    private static String requireName(String value) {
        String normalized = optional(value, 200, "customer name");
        if (normalized == null) {
            throw new CustomerDomainException("customer name must not be empty");
        }
        return normalized;
    }

    private static String requireEmail(String value) {
        String normalized = optional(value, 254, "customer email");
        if (normalized == null) {
            return null;
        }
        int at = normalized.indexOf('@');
        if (at <= 0 || at == normalized.length() - 1 || normalized.indexOf('@', at + 1) >= 0) {
            throw new CustomerDomainException("customer email is invalid");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String requireWebsite(String value) {
        String normalized = optional(value, MAX_WEBSITE_LENGTH, "customer website");
        if (normalized == null) {
            return null;
        }
        try {
            URI uri = URI.create(normalized);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new CustomerDomainException("customer website is invalid");
            }
            String persistentValue = uri.toASCIIString();
            if (persistentValue.length() > MAX_WEBSITE_LENGTH) {
                throw new CustomerDomainException("customer website is too long");
            }
            return persistentValue;
        } catch (IllegalArgumentException exception) {
            throw new CustomerDomainException("customer website is invalid");
        }
    }

    private static String optional(String value, int max, String description) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new CustomerDomainException(description + " is too long");
        }
        return normalized;
    }

    private static List<String> validateTags(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_TAGS) {
            throw new CustomerDomainException("customer tags exceed the supported limit");
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String value : values) {
            String tag = optional(value, 50, "customer tag");
            if (tag == null) {
                throw new CustomerDomainException("customer tag must not be empty");
            }
            unique.putIfAbsent(CustomerTagPolicy.key(tag), tag);
        }
        return List.copyOf(unique.values());
    }

    private static List<CustomerAddress> validateAddresses(Collection<CustomerAddress> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_ADDRESSES) {
            throw new CustomerDomainException("customer addresses exceed the supported limit");
        }
        Map<String, CustomerAddress> unique = values.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(CustomerAddress::key, Function.identity(),
                        (left, right) -> {
                            throw new CustomerDomainException("customer address keys must be unique");
                        }, LinkedHashMap::new));
        if (unique.size() != values.size()
                || unique.values().stream().filter(CustomerAddress::primary).count() > 1) {
            throw new CustomerDomainException("customer addresses are invalid");
        }
        return List.copyOf(unique.values());
    }

    private static List<CustomerCollaborator> validateCollaborators(
            Collection<CustomerCollaborator> values, MemberId ownerMemberId) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_COLLABORATORS) {
            throw new CustomerDomainException("customer collaborators exceed the supported limit");
        }
        Map<MemberId, CustomerCollaborator> unique = new LinkedHashMap<>();
        for (CustomerCollaborator value : values) {
            if (value == null || ownerMemberId != null && ownerMemberId.sameValueAs(value.memberId())
                    || unique.putIfAbsent(value.memberId(), value) != null) {
                throw new CustomerDomainException("customer collaborators are invalid");
            }
        }
        return List.copyOf(unique.values());
    }

    private static List<CustomerFieldValue> validateCustomFields(
            Collection<CustomerFieldValue> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_CUSTOM_FIELDS) {
            throw new CustomerDomainException("customer custom fields exceed the supported limit");
        }
        Set<FieldCode> codes = new LinkedHashSet<>();
        for (CustomerFieldValue value : values) {
            if (value == null || !codes.add(value.fieldCode())) {
                throw new CustomerDomainException("customer custom field codes must be unique");
            }
        }
        return List.copyOf(values);
    }
}
