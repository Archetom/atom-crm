package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.MarketingFormDomainException;
import io.github.archetom.crm.domain.model.MarketingFormStatus;
import io.github.archetom.crm.domain.model.MarketingFormTargetType;
import io.github.archetom.crm.domain.valueobject.MarketingFormId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tenant-owned public form configuration whose submissions create one supported CRM record type. */
@Getter
public class MarketingForm extends AggregateRoot<MarketingFormId> {

    public static final int MAX_FIELDS = 50;

    private MarketingFormId id;
    private TenantId tenantId;
    private MemberId createdByMemberId;
    private MemberId ownerMemberId;
    private MarketingFormTargetType targetType;
    private String publicKey;
    private String name;
    private String title;
    private String description;
    private String submitButtonText;
    private String successMessage;
    private MarketingFormStatus status;
    private List<MarketingFormField> fields = List.of();
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private MarketingForm() {
    }

    public record Snapshot(
            MarketingFormId id,
            TenantId tenantId,
            MemberId createdByMemberId,
            MemberId ownerMemberId,
            MarketingFormTargetType targetType,
            String publicKey,
            String name,
            String title,
            String description,
            String submitButtonText,
            String successMessage,
            MarketingFormStatus status,
            List<MarketingFormField> fields,
            Long version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime
    ) {
    }

    public static MarketingForm create(
            TenantId tenantId, MemberId createdByMemberId, MemberId ownerMemberId,
            String publicKey, String name, String title, String description,
            String submitButtonText, String successMessage,
            Collection<MarketingFormField> fields) {
        return create(tenantId, createdByMemberId, ownerMemberId,
                MarketingFormTargetType.LEAD, publicKey, name, title, description,
                submitButtonText, successMessage, fields);
    }

    public static MarketingForm create(
            TenantId tenantId, MemberId createdByMemberId, MemberId ownerMemberId,
            MarketingFormTargetType targetType, String publicKey, String name, String title,
            String description, String submitButtonText, String successMessage,
            Collection<MarketingFormField> fields) {
        MarketingForm form = new MarketingForm();
        form.tenantId = requireTenant(tenantId);
        form.createdByMemberId = requireMember(createdByMemberId, "marketing form creator");
        form.ownerMemberId = requireMember(ownerMemberId, "marketing form owner");
        form.targetType = requireTargetType(targetType);
        form.publicKey = requirePublicKey(publicKey);
        form.status = MarketingFormStatus.INACTIVE;
        form.apply(name, title, description, submitButtonText, successMessage, fields);
        form.createdTime = form.updatedTime;
        return form;
    }

    public static MarketingForm reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new MarketingFormDomainException("marketing form snapshot is required");
        }
        MarketingForm form = new MarketingForm();
        form.id = snapshot.id();
        form.tenantId = requireTenant(snapshot.tenantId());
        form.createdByMemberId = requireMember(
                snapshot.createdByMemberId(), "marketing form creator");
        form.ownerMemberId = requireMember(snapshot.ownerMemberId(), "marketing form owner");
        form.targetType = requireTargetType(snapshot.targetType());
        form.publicKey = requirePublicKey(snapshot.publicKey());
        form.name = requiredText(snapshot.name(), 100, "marketing form name");
        form.title = requiredText(snapshot.title(), 200, "marketing form title");
        form.description = optionalText(
                snapshot.description(), 2000, "marketing form description");
        form.submitButtonText = requiredText(
                snapshot.submitButtonText(), 50, "marketing form submit button text");
        form.successMessage = requiredText(
                snapshot.successMessage(), 500, "marketing form success message");
        form.status = snapshot.status() == null
                ? MarketingFormStatus.INACTIVE : snapshot.status();
        form.fields = validateFields(snapshot.fields());
        form.restoreVersion(snapshot.version());
        form.createdTime = snapshot.createdTime();
        form.updatedTime = snapshot.updatedTime();
        return form;
    }

    public void update(
            MemberId ownerMemberId, String name, String title, String description,
            String submitButtonText, String successMessage,
            Collection<MarketingFormField> fields) {
        ensureNotDeleted();
        this.ownerMemberId = requireMember(ownerMemberId, "marketing form owner");
        apply(name, title, description, submitButtonText, successMessage, fields);
    }

    public void activate() {
        ensureNotDeleted();
        status = MarketingFormStatus.ACTIVE;
        touch();
    }

    public void deactivate() {
        ensureNotDeleted();
        status = MarketingFormStatus.INACTIVE;
        touch();
    }

    public void delete() {
        ensureNotDeleted();
        if (status == MarketingFormStatus.ACTIVE) {
            throw new MarketingFormDomainException(
                    "active marketing forms must be deactivated before deletion");
        }
        status = MarketingFormStatus.DELETED;
        touch();
    }

    public boolean isActive() {
        return status == MarketingFormStatus.ACTIVE;
    }

    public boolean isDeleted() {
        return status == MarketingFormStatus.DELETED;
    }

    public void onPersisted(MarketingFormId id, Long version,
                            LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new MarketingFormDomainException("persisted marketing form ID is invalid");
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
            String name, String title, String description,
            String submitButtonText, String successMessage,
            Collection<MarketingFormField> fields) {
        this.name = requiredText(name, 100, "marketing form name");
        this.title = requiredText(title, 200, "marketing form title");
        this.description = optionalText(description, 2000, "marketing form description");
        this.submitButtonText = defaultText(
                submitButtonText, "Submit", 50, "marketing form submit button text");
        this.successMessage = defaultText(
                successMessage, "Submitted successfully", 500,
                "marketing form success message");
        this.fields = validateFields(fields);
        touch();
    }

    private void ensureNotDeleted() {
        if (isDeleted()) {
            throw new MarketingFormDomainException("deleted marketing forms are immutable");
        }
    }

    private static List<MarketingFormField> validateFields(
            Collection<MarketingFormField> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new MarketingFormDomainException("marketing form fields are required");
        }
        if (fields.size() > MAX_FIELDS) {
            throw new MarketingFormDomainException(
                    "marketing form fields exceed the supported limit");
        }
        Map<String, MarketingFormField> unique = new LinkedHashMap<>();
        for (MarketingFormField field : fields) {
            if (field == null || unique.putIfAbsent(
                    field.fieldCode().getValue(), field) != null) {
                throw new MarketingFormDomainException(
                        "marketing form field codes must be unique");
            }
        }
        return unique.values().stream()
                .sorted(Comparator.comparingInt(MarketingFormField::sortOrder)
                        .thenComparing(value -> value.fieldCode().getValue()))
                .toList();
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new MarketingFormDomainException("marketing form tenant is required");
        }
        return value;
    }

    private static MemberId requireMember(MemberId value, String description) {
        if (value == null) {
            throw new MarketingFormDomainException(description + " is required");
        }
        return value;
    }

    private static MarketingFormTargetType requireTargetType(MarketingFormTargetType value) {
        if (value == null) {
            throw new MarketingFormDomainException("marketing form target type is required");
        }
        return value;
    }

    private static String requirePublicKey(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{16,64}")) {
            throw new MarketingFormDomainException("marketing form public key is invalid");
        }
        return value;
    }

    private static String requiredText(String value, int max, String description) {
        if (value == null || value.isBlank()) {
            throw new MarketingFormDomainException(description + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new MarketingFormDomainException(description + " is too long");
        }
        return normalized;
    }

    private static String defaultText(
            String value, String fallback, int max, String description) {
        return requiredText(value == null || value.isBlank() ? fallback : value, max, description);
    }

    private static String optionalText(String value, int max, String description) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new MarketingFormDomainException(description + " is too long");
        }
        return normalized;
    }

    private void touch() {
        updatedTime = LocalDateTime.now();
    }
}
