package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.ContactDomainException;
import io.github.archetom.crm.domain.model.ContactDealStatus;
import io.github.archetom.crm.domain.model.ContactStatus;
import io.github.archetom.crm.domain.policy.ContactTagPolicy;
import io.github.archetom.crm.domain.valueobject.ContactId;
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

/** Tenant-owned contact aggregate and its bounded child values. */
@Getter
public class Contact extends AggregateRoot<ContactId> {

    public static final int MAX_TAGS = 50;
    public static final int MAX_ADDRESSES = 20;
    public static final int MAX_COLLABORATORS = 100;
    public static final int MAX_CUSTOM_FIELDS = 200;
    private static final int MAX_WEBSITE_LENGTH = 500;
    private static final int MAX_JOB_TITLE_LENGTH = 200;

    private ContactId id;
    private TenantId tenantId;
    private MemberId ownerMemberId;
    private CustomerId customerId;
    private ContactId parentContactId;
    private String name;
    private String phoneNumber;
    private String telephone;
    private String jobTitle;
    private String email;
    private String website;
    private String description;
    private LocalDateTime nextContactTime;
    private ContactStatus status;
    private boolean locked;
    private ContactDealStatus dealStatus;
    private LocalDateTime dealTime;
    private List<String> tags = List.of();
    private List<ContactAddress> addresses = List.of();
    private List<ContactCollaborator> collaborators = List.of();
    private List<ContactFieldValue> customFieldValues = List.of();
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private Contact() {
    }

    public record Snapshot(
            ContactId id,
            TenantId tenantId,
            MemberId ownerMemberId,
            CustomerId customerId,
            ContactId parentContactId,
            String name,
            String phoneNumber,
            String telephone,
            String jobTitle,
            String email,
            String website,
            String description,
            LocalDateTime nextContactTime,
            ContactStatus status,
            boolean locked,
            ContactDealStatus dealStatus,
            LocalDateTime dealTime,
            List<String> tags,
            List<ContactAddress> addresses,
            List<ContactCollaborator> collaborators,
            List<ContactFieldValue> customFieldValues,
            Long version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
    }

    public static Contact create(
            TenantId tenantId,
            MemberId ownerMemberId,
            String name,
            String phoneNumber,
            String telephone,
            String jobTitle,
            String email,
            String website,
            String description,
            LocalDateTime nextContactTime,
            Collection<ContactAddress> addresses,
            Collection<ContactFieldValue> customFieldValues) {
        Contact contact = new Contact();
        contact.tenantId = requireTenant(tenantId);
        contact.ownerMemberId = requireOwner(ownerMemberId, ContactStatus.ACTIVE);
        contact.status = ContactStatus.ACTIVE;
        contact.locked = false;
        contact.dealStatus = ContactDealStatus.OPEN;
        contact.applyDetails(name, phoneNumber, telephone, jobTitle, email, website, description,
                nextContactTime,
                addresses, customFieldValues);
        contact.createdTime = contact.updatedTime;
        return contact;
    }

    public static Contact create(
            TenantId tenantId,
            MemberId ownerMemberId,
            String name,
            String phoneNumber,
            String telephone,
            String email,
            String website,
            String description,
            Collection<ContactAddress> addresses,
            Collection<ContactFieldValue> customFieldValues) {
        return create(tenantId, ownerMemberId, name, phoneNumber, telephone, null, email, website,
                description, null, addresses, customFieldValues);
    }

    public static Contact reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new ContactDomainException("contact snapshot must not be empty");
        }
        Contact contact = new Contact();
        contact.id = snapshot.id();
        contact.tenantId = requireTenant(snapshot.tenantId());
        contact.status = snapshot.status() == null ? ContactStatus.DELETED : snapshot.status();
        contact.ownerMemberId = requireOwner(snapshot.ownerMemberId(), contact.status);
        contact.customerId = snapshot.customerId();
        contact.parentContactId = snapshot.parentContactId();
        contact.name = requireName(snapshot.name());
        contact.phoneNumber = optional(snapshot.phoneNumber(), 30, "contact phone number");
        contact.telephone = optional(snapshot.telephone(), 50, "contact telephone");
        contact.jobTitle = optional(snapshot.jobTitle(), MAX_JOB_TITLE_LENGTH, "contact job title");
        contact.email = requireEmail(snapshot.email());
        contact.website = requireWebsite(snapshot.website());
        contact.description = optional(snapshot.description(), 2000, "contact description");
        contact.nextContactTime = snapshot.nextContactTime();
        contact.locked = snapshot.locked();
        contact.dealStatus = snapshot.dealStatus() == null ? ContactDealStatus.OPEN : snapshot.dealStatus();
        contact.dealTime = snapshot.dealTime();
        contact.tags = validateTags(snapshot.tags());
        contact.addresses = validateAddresses(snapshot.addresses());
        contact.collaborators = validateCollaborators(
                snapshot.collaborators(), contact.ownerMemberId);
        contact.customFieldValues = validateCustomFields(snapshot.customFieldValues());
        contact.restoreVersion(snapshot.version());
        contact.createdTime = snapshot.createdTime();
        contact.updatedTime = snapshot.updatedTime();
        return contact;
    }

    public void replaceDetails(
            String name,
            String phoneNumber,
            String telephone,
            String jobTitle,
            String email,
            String website,
            String description,
            LocalDateTime nextContactTime,
            Collection<ContactAddress> addresses,
            Collection<ContactFieldValue> customFieldValues) {
        ensureMutable();
        applyDetails(name, phoneNumber, telephone, jobTitle, email, website, description,
                nextContactTime,
                addresses, customFieldValues);
    }

    public void replaceDetails(
            String name,
            String phoneNumber,
            String telephone,
            String email,
            String website,
            String description,
            Collection<ContactAddress> addresses,
            Collection<ContactFieldValue> customFieldValues) {
        replaceDetails(name, phoneNumber, telephone, jobTitle, email, website, description,
                nextContactTime, addresses, customFieldValues);
    }

    public void changeState(boolean locked, ContactDealStatus dealStatus) {
        ensureMutable();
        ContactDealStatus nextDealStatus = dealStatus == null ? ContactDealStatus.OPEN : dealStatus;
        if (this.dealStatus != ContactDealStatus.WON && nextDealStatus == ContactDealStatus.WON) {
            dealTime = LocalDateTime.now();
        } else if (this.dealStatus == ContactDealStatus.WON && nextDealStatus != ContactDealStatus.WON) {
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

    public void changeParent(ContactId parentContactId) {
        ensureMutable();
        if (id != null && id.sameValueAs(parentContactId)) {
            throw new ContactDomainException("a contact cannot be its own parent");
        }
        this.parentContactId = parentContactId;
        touch();
    }

    /** Associates the contact with an already tenant-validated customer. */
    public void changeCustomer(CustomerId customerId) {
        ensureMutable();
        this.customerId = customerId;
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
            values.put(ContactTagPolicy.key(tag), tag);
        }
        for (String tag : validateTags(addedTags)) {
            values.putIfAbsent(ContactTagPolicy.key(tag), tag);
        }
        for (String tag : validateTags(removedTags)) {
            values.remove(ContactTagPolicy.key(tag));
        }
        this.tags = validateTags(values.values());
        touch();
    }

    public void replaceCollaborators(Collection<ContactCollaborator> collaborators) {
        ensureMutable();
        this.collaborators = validateCollaborators(collaborators, ownerMemberId);
        touch();
    }

    public boolean exitCollaborator(MemberId memberId) {
        ensureMutable();
        if (memberId == null || ownerMemberId.sameValueAs(memberId)) {
            throw new ContactDomainException("contact owner cannot exit as a collaborator");
        }
        List<ContactCollaborator> remaining = collaborators.stream()
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
        status = ContactStatus.DELETED;
        touch();
    }

    /** Removes ownership when a visible active contact enters a tenant pool. */
    public void enterPool() {
        ensureMutable();
        ownerMemberId = null;
        collaborators = List.of();
        status = ContactStatus.IN_POOL;
        touch();
    }

    /** Restores ownership when a pool contact is claimed or assigned. */
    public void leavePool(MemberId ownerMemberId) {
        if (status != ContactStatus.IN_POOL) {
            throw new ContactDomainException("only pool contacts can leave a pool");
        }
        this.ownerMemberId = requireOwner(ownerMemberId, ContactStatus.ACTIVE);
        status = ContactStatus.ACTIVE;
        touch();
    }

    /** Soft-deletes a pool contact without making it active first. */
    public void deleteFromPool() {
        if (status != ContactStatus.IN_POOL) {
            throw new ContactDomainException("only pool contacts can be deleted from a pool");
        }
        status = ContactStatus.DELETED;
        touch();
    }

    public boolean isActive() {
        return status == ContactStatus.ACTIVE;
    }

    public boolean isDeleted() {
        return status == ContactStatus.DELETED;
    }

    public void onPersisted(ContactId id, Long version,
                            LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new ContactDomainException("persisted contact ID is invalid");
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
            String jobTitle,
            String email,
            String website,
            String description,
            LocalDateTime nextContactTime,
            Collection<ContactAddress> addresses,
            Collection<ContactFieldValue> customFieldValues) {
        String nextName = requireName(name);
        String nextPhoneNumber = optional(phoneNumber, 30, "contact phone number");
        String nextTelephone = optional(telephone, 50, "contact telephone");
        String nextJobTitle = optional(jobTitle, MAX_JOB_TITLE_LENGTH, "contact job title");
        String nextEmail = requireEmail(email);
        String nextWebsite = requireWebsite(website);
        String nextDescription = optional(description, 2000, "contact description");
        List<ContactAddress> nextAddresses = validateAddresses(addresses);
        List<ContactFieldValue> nextCustomFields = validateCustomFields(customFieldValues);

        this.name = nextName;
        this.phoneNumber = nextPhoneNumber;
        this.telephone = nextTelephone;
        this.jobTitle = nextJobTitle;
        this.email = nextEmail;
        this.website = nextWebsite;
        this.description = nextDescription;
        this.nextContactTime = nextContactTime;
        this.addresses = nextAddresses;
        this.customFieldValues = nextCustomFields;
        touch();
    }

    private void ensureMutable() {
        if (status != ContactStatus.ACTIVE) {
            throw new ContactDomainException("only active contacts can change");
        }
    }

    private void touch() {
        updatedTime = LocalDateTime.now();
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new ContactDomainException("contact tenant must not be empty");
        }
        return value;
    }

    private static MemberId requireOwner(MemberId value, ContactStatus status) {
        if (value == null && status == ContactStatus.ACTIVE) {
            throw new ContactDomainException("active contact owner must not be empty");
        }
        return value;
    }

    private static String requireName(String value) {
        String normalized = optional(value, 200, "contact name");
        if (normalized == null) {
            throw new ContactDomainException("contact name must not be empty");
        }
        return normalized;
    }

    private static String requireEmail(String value) {
        String normalized = optional(value, 254, "contact email");
        if (normalized == null) {
            return null;
        }
        int at = normalized.indexOf('@');
        if (at <= 0 || at == normalized.length() - 1 || normalized.indexOf('@', at + 1) >= 0) {
            throw new ContactDomainException("contact email is invalid");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String requireWebsite(String value) {
        String normalized = optional(value, MAX_WEBSITE_LENGTH, "contact website");
        if (normalized == null) {
            return null;
        }
        try {
            URI uri = URI.create(normalized);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new ContactDomainException("contact website is invalid");
            }
            String persistentValue = uri.toASCIIString();
            if (persistentValue.length() > MAX_WEBSITE_LENGTH) {
                throw new ContactDomainException("contact website is too long");
            }
            return persistentValue;
        } catch (IllegalArgumentException exception) {
            throw new ContactDomainException("contact website is invalid");
        }
    }

    private static String optional(String value, int max, String description) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new ContactDomainException(description + " is too long");
        }
        return normalized;
    }

    private static List<String> validateTags(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_TAGS) {
            throw new ContactDomainException("contact tags exceed the supported limit");
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String value : values) {
            String tag = optional(value, 50, "contact tag");
            if (tag == null) {
                throw new ContactDomainException("contact tag must not be empty");
            }
            unique.putIfAbsent(ContactTagPolicy.key(tag), tag);
        }
        return List.copyOf(unique.values());
    }

    private static List<ContactAddress> validateAddresses(Collection<ContactAddress> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_ADDRESSES) {
            throw new ContactDomainException("contact addresses exceed the supported limit");
        }
        Map<String, ContactAddress> unique = values.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(ContactAddress::key, Function.identity(),
                        (left, right) -> {
                            throw new ContactDomainException("contact address keys must be unique");
                        }, LinkedHashMap::new));
        if (unique.size() != values.size()
                || unique.values().stream().filter(ContactAddress::primary).count() > 1) {
            throw new ContactDomainException("contact addresses are invalid");
        }
        return List.copyOf(unique.values());
    }

    private static List<ContactCollaborator> validateCollaborators(
            Collection<ContactCollaborator> values, MemberId ownerMemberId) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_COLLABORATORS) {
            throw new ContactDomainException("contact collaborators exceed the supported limit");
        }
        Map<MemberId, ContactCollaborator> unique = new LinkedHashMap<>();
        for (ContactCollaborator value : values) {
            if (value == null || ownerMemberId != null && ownerMemberId.sameValueAs(value.memberId())
                    || unique.putIfAbsent(value.memberId(), value) != null) {
                throw new ContactDomainException("contact collaborators are invalid");
            }
        }
        return List.copyOf(unique.values());
    }

    private static List<ContactFieldValue> validateCustomFields(
            Collection<ContactFieldValue> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_CUSTOM_FIELDS) {
            throw new ContactDomainException("contact custom fields exceed the supported limit");
        }
        Set<FieldCode> codes = new LinkedHashSet<>();
        for (ContactFieldValue value : values) {
            if (value == null || !codes.add(value.fieldCode())) {
                throw new ContactDomainException("contact custom field codes must be unique");
            }
        }
        return List.copyOf(values);
    }
}
