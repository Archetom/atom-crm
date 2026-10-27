package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.LeadDomainException;
import io.github.archetom.crm.domain.model.LeadDealStatus;
import io.github.archetom.crm.domain.model.LeadStatus;
import io.github.archetom.crm.domain.policy.LeadTagPolicy;
import io.github.archetom.crm.domain.valueobject.LeadId;
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

/** Tenant-owned lead aggregate and its bounded child values. */
@Getter
public class Lead extends AggregateRoot<LeadId> {

    public static final int MAX_TAGS = 50;
    public static final int MAX_ADDRESSES = 20;
    public static final int MAX_COLLABORATORS = 100;
    public static final int MAX_CUSTOM_FIELDS = 200;
    private static final int MAX_WEBSITE_LENGTH = 500;
    private static final int MAX_JOB_TITLE_LENGTH = 200;

    private LeadId id;
    private TenantId tenantId;
    private MemberId ownerMemberId;
    private CustomerId customerId;
    private ContactId convertedContactId;
    private LeadId parentLeadId;
    private String name;
    private String phoneNumber;
    private String telephone;
    private String jobTitle;
    private String email;
    private String website;
    private String description;
    private LocalDateTime nextContactTime;
    private LeadStatus status;
    private boolean locked;
    private LeadDealStatus dealStatus;
    private LocalDateTime dealTime;
    private List<String> tags = List.of();
    private List<LeadAddress> addresses = List.of();
    private List<LeadCollaborator> collaborators = List.of();
    private List<LeadFieldValue> customFieldValues = List.of();
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private Lead() {
    }

    public record Snapshot(
            LeadId id,
            TenantId tenantId,
            MemberId ownerMemberId,
            CustomerId customerId,
            ContactId convertedContactId,
            LeadId parentLeadId,
            String name,
            String phoneNumber,
            String telephone,
            String jobTitle,
            String email,
            String website,
            String description,
            LocalDateTime nextContactTime,
            LeadStatus status,
            boolean locked,
            LeadDealStatus dealStatus,
            LocalDateTime dealTime,
            List<String> tags,
            List<LeadAddress> addresses,
            List<LeadCollaborator> collaborators,
            List<LeadFieldValue> customFieldValues,
            Long version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
    }

    public static Lead create(
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
            Collection<LeadAddress> addresses,
            Collection<LeadFieldValue> customFieldValues) {
        Lead lead = new Lead();
        lead.tenantId = requireTenant(tenantId);
        lead.ownerMemberId = requireOwner(ownerMemberId, LeadStatus.ACTIVE);
        lead.status = LeadStatus.ACTIVE;
        lead.locked = false;
        lead.dealStatus = LeadDealStatus.OPEN;
        lead.applyDetails(name, phoneNumber, telephone, jobTitle, email, website, description,
                nextContactTime,
                addresses, customFieldValues);
        lead.createdTime = lead.updatedTime;
        return lead;
    }

    public static Lead create(
            TenantId tenantId,
            MemberId ownerMemberId,
            String name,
            String phoneNumber,
            String telephone,
            String email,
            String website,
            String description,
            Collection<LeadAddress> addresses,
            Collection<LeadFieldValue> customFieldValues) {
        return create(tenantId, ownerMemberId, name, phoneNumber, telephone, null, email, website,
                description, null, addresses, customFieldValues);
    }

    public static Lead reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new LeadDomainException("lead snapshot must not be empty");
        }
        Lead lead = new Lead();
        lead.id = snapshot.id();
        lead.tenantId = requireTenant(snapshot.tenantId());
        lead.status = snapshot.status() == null ? LeadStatus.DELETED : snapshot.status();
        lead.ownerMemberId = requireOwner(snapshot.ownerMemberId(), lead.status);
        lead.customerId = snapshot.customerId();
        lead.convertedContactId = snapshot.convertedContactId();
        validateConversionAssociation(lead.status, lead.customerId, lead.convertedContactId);
        lead.parentLeadId = snapshot.parentLeadId();
        lead.name = requireName(snapshot.name());
        lead.phoneNumber = optional(snapshot.phoneNumber(), 30, "lead phone number");
        lead.telephone = optional(snapshot.telephone(), 50, "lead telephone");
        lead.jobTitle = optional(snapshot.jobTitle(), MAX_JOB_TITLE_LENGTH, "lead job title");
        lead.email = requireEmail(snapshot.email());
        lead.website = requireWebsite(snapshot.website());
        lead.description = optional(snapshot.description(), 2000, "lead description");
        lead.nextContactTime = snapshot.nextContactTime();
        lead.locked = snapshot.locked();
        lead.dealStatus = snapshot.dealStatus() == null ? LeadDealStatus.OPEN : snapshot.dealStatus();
        lead.dealTime = snapshot.dealTime();
        lead.tags = validateTags(snapshot.tags());
        lead.addresses = validateAddresses(snapshot.addresses());
        lead.collaborators = validateCollaborators(
                snapshot.collaborators(), lead.ownerMemberId);
        lead.customFieldValues = validateCustomFields(snapshot.customFieldValues());
        lead.restoreVersion(snapshot.version());
        lead.createdTime = snapshot.createdTime();
        lead.updatedTime = snapshot.updatedTime();
        return lead;
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
            Collection<LeadAddress> addresses,
            Collection<LeadFieldValue> customFieldValues) {
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
            Collection<LeadAddress> addresses,
            Collection<LeadFieldValue> customFieldValues) {
        replaceDetails(name, phoneNumber, telephone, jobTitle, email, website, description,
                nextContactTime, addresses, customFieldValues);
    }

    public void changeState(boolean locked, LeadDealStatus dealStatus) {
        ensureMutable();
        LeadDealStatus nextDealStatus = dealStatus == null ? LeadDealStatus.OPEN : dealStatus;
        if (this.dealStatus != LeadDealStatus.WON && nextDealStatus == LeadDealStatus.WON) {
            dealTime = LocalDateTime.now();
        } else if (this.dealStatus == LeadDealStatus.WON && nextDealStatus != LeadDealStatus.WON) {
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

    public void changeParent(LeadId parentLeadId) {
        ensureMutable();
        if (id != null && id.sameValueAs(parentLeadId)) {
            throw new LeadDomainException("a lead cannot be its own parent");
        }
        this.parentLeadId = parentLeadId;
        touch();
    }

    /** Associates the lead with an already tenant-validated customer. */
    public void changeCustomer(CustomerId customerId) {
        ensureMutable();
        this.customerId = customerId;
        touch();
    }

    /** Finalizes the lead only after its customer/contact projections have been persisted. */
    public void convert(CustomerId customerId, ContactId convertedContactId) {
        ensureMutable();
        if (customerId == null) {
            throw new LeadDomainException("converted lead customer must not be empty");
        }
        if (convertedContactId == null) {
            throw new LeadDomainException("converted lead contact must not be empty");
        }
        this.customerId = customerId;
        this.convertedContactId = convertedContactId;
        this.status = LeadStatus.CONVERTED;
        if (this.dealStatus != LeadDealStatus.WON || this.dealTime == null) {
            this.dealTime = LocalDateTime.now();
        }
        this.dealStatus = LeadDealStatus.WON;
        touch();
    }

    private static void validateConversionAssociation(
            LeadStatus status, CustomerId customerId, ContactId convertedContactId) {
        if (status == LeadStatus.CONVERTED
                && (customerId == null || convertedContactId == null)) {
            throw new LeadDomainException("converted lead associations must not be empty");
        }
        if (status != LeadStatus.CONVERTED && convertedContactId != null) {
            throw new LeadDomainException("only converted leads may reference a converted contact");
        }
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
            values.put(LeadTagPolicy.key(tag), tag);
        }
        for (String tag : validateTags(addedTags)) {
            values.putIfAbsent(LeadTagPolicy.key(tag), tag);
        }
        for (String tag : validateTags(removedTags)) {
            values.remove(LeadTagPolicy.key(tag));
        }
        this.tags = validateTags(values.values());
        touch();
    }

    public void replaceCollaborators(Collection<LeadCollaborator> collaborators) {
        ensureMutable();
        this.collaborators = validateCollaborators(collaborators, ownerMemberId);
        touch();
    }

    public boolean exitCollaborator(MemberId memberId) {
        ensureMutable();
        if (memberId == null || ownerMemberId.sameValueAs(memberId)) {
            throw new LeadDomainException("lead owner cannot exit as a collaborator");
        }
        List<LeadCollaborator> remaining = collaborators.stream()
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
        status = LeadStatus.DELETED;
        touch();
    }

    public boolean isActive() {
        return status == LeadStatus.ACTIVE;
    }

    public boolean isDeleted() {
        return status == LeadStatus.DELETED;
    }

    public void onPersisted(LeadId id, Long version,
                            LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new LeadDomainException("persisted lead ID is invalid");
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
            Collection<LeadAddress> addresses,
            Collection<LeadFieldValue> customFieldValues) {
        String nextName = requireName(name);
        String nextPhoneNumber = optional(phoneNumber, 30, "lead phone number");
        String nextTelephone = optional(telephone, 50, "lead telephone");
        String nextJobTitle = optional(jobTitle, MAX_JOB_TITLE_LENGTH, "lead job title");
        String nextEmail = requireEmail(email);
        String nextWebsite = requireWebsite(website);
        String nextDescription = optional(description, 2000, "lead description");
        List<LeadAddress> nextAddresses = validateAddresses(addresses);
        List<LeadFieldValue> nextCustomFields = validateCustomFields(customFieldValues);

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
        if (status != LeadStatus.ACTIVE) {
            throw new LeadDomainException("only active leads can change");
        }
    }

    private void touch() {
        updatedTime = LocalDateTime.now();
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new LeadDomainException("lead tenant must not be empty");
        }
        return value;
    }

    private static MemberId requireOwner(MemberId value, LeadStatus status) {
        if (value == null && status == LeadStatus.ACTIVE) {
            throw new LeadDomainException("active lead owner must not be empty");
        }
        return value;
    }

    private static String requireName(String value) {
        String normalized = optional(value, 200, "lead name");
        if (normalized == null) {
            throw new LeadDomainException("lead name must not be empty");
        }
        return normalized;
    }

    private static String requireEmail(String value) {
        String normalized = optional(value, 254, "lead email");
        if (normalized == null) {
            return null;
        }
        int at = normalized.indexOf('@');
        if (at <= 0 || at == normalized.length() - 1 || normalized.indexOf('@', at + 1) >= 0) {
            throw new LeadDomainException("lead email is invalid");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String requireWebsite(String value) {
        String normalized = optional(value, MAX_WEBSITE_LENGTH, "lead website");
        if (normalized == null) {
            return null;
        }
        try {
            URI uri = URI.create(normalized);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new LeadDomainException("lead website is invalid");
            }
            String persistentValue = uri.toASCIIString();
            if (persistentValue.length() > MAX_WEBSITE_LENGTH) {
                throw new LeadDomainException("lead website is too long");
            }
            return persistentValue;
        } catch (IllegalArgumentException exception) {
            throw new LeadDomainException("lead website is invalid");
        }
    }

    private static String optional(String value, int max, String description) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new LeadDomainException(description + " is too long");
        }
        return normalized;
    }

    private static List<String> validateTags(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_TAGS) {
            throw new LeadDomainException("lead tags exceed the supported limit");
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String value : values) {
            String tag = optional(value, 50, "lead tag");
            if (tag == null) {
                throw new LeadDomainException("lead tag must not be empty");
            }
            unique.putIfAbsent(LeadTagPolicy.key(tag), tag);
        }
        return List.copyOf(unique.values());
    }

    private static List<LeadAddress> validateAddresses(Collection<LeadAddress> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_ADDRESSES) {
            throw new LeadDomainException("lead addresses exceed the supported limit");
        }
        Map<String, LeadAddress> unique = values.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(LeadAddress::key, Function.identity(),
                        (left, right) -> {
                            throw new LeadDomainException("lead address keys must be unique");
                        }, LinkedHashMap::new));
        if (unique.size() != values.size()
                || unique.values().stream().filter(LeadAddress::primary).count() > 1) {
            throw new LeadDomainException("lead addresses are invalid");
        }
        return List.copyOf(unique.values());
    }

    private static List<LeadCollaborator> validateCollaborators(
            Collection<LeadCollaborator> values, MemberId ownerMemberId) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_COLLABORATORS) {
            throw new LeadDomainException("lead collaborators exceed the supported limit");
        }
        Map<MemberId, LeadCollaborator> unique = new LinkedHashMap<>();
        for (LeadCollaborator value : values) {
            if (value == null || ownerMemberId != null && ownerMemberId.sameValueAs(value.memberId())
                    || unique.putIfAbsent(value.memberId(), value) != null) {
                throw new LeadDomainException("lead collaborators are invalid");
            }
        }
        return List.copyOf(unique.values());
    }

    private static List<LeadFieldValue> validateCustomFields(
            Collection<LeadFieldValue> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_CUSTOM_FIELDS) {
            throw new LeadDomainException("lead custom fields exceed the supported limit");
        }
        Set<FieldCode> codes = new LinkedHashSet<>();
        for (LeadFieldValue value : values) {
            if (value == null || !codes.add(value.fieldCode())) {
                throw new LeadDomainException("lead custom field codes must be unique");
            }
        }
        return List.copyOf(values);
    }
}
