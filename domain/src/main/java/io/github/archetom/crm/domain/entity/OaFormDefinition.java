package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.OaFormDomainException;
import io.github.archetom.crm.domain.model.OaFormStatus;
import io.github.archetom.crm.domain.policy.IdentifierKeyNormalizer;
import io.github.archetom.crm.domain.policy.OaRequestFieldCatalog;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaFormId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned tenant OA form composed from the governed OA_REQUEST field catalog. */
@Getter
public class OaFormDefinition extends AggregateRoot<OaFormId> {

    public static final int MAX_FIELDS = 100;

    private OaFormId id;
    private TenantId tenantId;
    private MemberId createdByMemberId;
    private String code;
    private String normalizedCode;
    private String name;
    private String description;
    private ApprovalDefinitionId approvalDefinitionId;
    private OaFormStatus status;
    private List<OaFormField> fields = List.of();
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private OaFormDefinition() {
    }

    public record Snapshot(
            OaFormId id,
            TenantId tenantId,
            MemberId createdByMemberId,
            String code,
            String name,
            String description,
            ApprovalDefinitionId approvalDefinitionId,
            OaFormStatus status,
            List<OaFormField> fields,
            Long version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
    }

    public static OaFormDefinition create(
            TenantId tenantId, MemberId createdByMemberId, String code, String name,
            String description, ApprovalDefinitionId approvalDefinitionId,
            Collection<OaFormField> fields) {
        OaFormDefinition form = new OaFormDefinition();
        form.tenantId = requireTenant(tenantId);
        form.createdByMemberId = requireMember(createdByMemberId);
        form.status = OaFormStatus.INACTIVE;
        form.apply(code, name, description, approvalDefinitionId, fields);
        form.createdTime = form.updatedTime;
        return form;
    }

    public static OaFormDefinition reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new OaFormDomainException("OA form snapshot is required");
        }
        OaFormDefinition form = new OaFormDefinition();
        form.id = snapshot.id();
        form.tenantId = requireTenant(snapshot.tenantId());
        form.createdByMemberId = requireMember(snapshot.createdByMemberId());
        form.status = snapshot.status() == null ? OaFormStatus.INACTIVE : snapshot.status();
        form.apply(snapshot.code(), snapshot.name(), snapshot.description(),
                snapshot.approvalDefinitionId(), snapshot.fields());
        form.restoreVersion(snapshot.version());
        form.createdTime = snapshot.createdTime();
        form.updatedTime = snapshot.updatedTime();
        return form;
    }

    public void update(String code, String name, String description,
                       ApprovalDefinitionId approvalDefinitionId,
                       Collection<OaFormField> fields) {
        ensureNotDeleted();
        apply(code, name, description, approvalDefinitionId, fields);
    }

    public void activate() {
        ensureNotDeleted();
        status = OaFormStatus.ACTIVE;
        touch();
    }

    public void deactivate() {
        ensureNotDeleted();
        status = OaFormStatus.INACTIVE;
        touch();
    }

    public void delete() {
        ensureNotDeleted();
        if (isActive()) {
            throw new OaFormDomainException(
                    "active OA forms must be deactivated before deletion");
        }
        status = OaFormStatus.DELETED;
        touch();
    }

    public boolean isActive() {
        return status == OaFormStatus.ACTIVE;
    }

    public boolean isDeleted() {
        return status == OaFormStatus.DELETED;
    }

    public void onPersisted(OaFormId persistedId, Long persistedVersion,
                            LocalDateTime created, LocalDateTime updated) {
        if (persistedId == null || id != null && !id.sameValueAs(persistedId)) {
            throw new OaFormDomainException("persisted OA form ID is invalid");
        }
        id = persistedId;
        restoreVersion(persistedVersion);
        if (created != null) {
            createdTime = created;
        }
        if (updated != null) {
            updatedTime = updated;
        }
    }

    private void apply(String code, String name, String description,
                       ApprovalDefinitionId approvalDefinitionId,
                       Collection<OaFormField> fields) {
        this.code = requiredText(code, 100, "OA form code");
        normalizedCode = IdentifierKeyNormalizer.normalize(this.code);
        if (normalizedCode.length() > 200) {
            throw new OaFormDomainException("normalized OA form code is too long");
        }
        this.name = requiredText(name, 200, "OA form name");
        this.description = optionalText(description, 2000, "OA form description");
        if (approvalDefinitionId == null) {
            throw new OaFormDomainException("OA form approval definition is required");
        }
        this.approvalDefinitionId = approvalDefinitionId;
        this.fields = validateFields(fields);
        touch();
    }

    private static List<OaFormField> validateFields(Collection<OaFormField> fields) {
        if (fields == null || fields.isEmpty() || fields.size() > MAX_FIELDS) {
            throw new OaFormDomainException("OA form fields are invalid");
        }
        Map<String, OaFormField> unique = new LinkedHashMap<>();
        for (OaFormField field : fields) {
            if (field == null || unique.putIfAbsent(
                    field.fieldCode().getValue(), field) != null) {
                throw new OaFormDomainException("OA form field codes must be unique");
            }
        }
        OaFormField title = unique.get(OaRequestFieldCatalog.REQUEST_TITLE);
        if (title == null || !title.required()) {
            throw new OaFormDomainException("request_title must be a required OA form field");
        }
        return unique.values().stream()
                .sorted(Comparator.comparingInt(OaFormField::sortOrder)
                        .thenComparing(value -> value.fieldCode().getValue()))
                .toList();
    }

    private void ensureNotDeleted() {
        if (isDeleted()) {
            throw new OaFormDomainException("deleted OA forms are immutable");
        }
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new OaFormDomainException("OA form tenant is required");
        }
        return value;
    }

    private static MemberId requireMember(MemberId value) {
        if (value == null) {
            throw new OaFormDomainException("OA form creator is required");
        }
        return value;
    }

    private static String requiredText(String value, int maximum, String description) {
        String normalized = optionalText(value, maximum, description);
        if (normalized == null) {
            throw new OaFormDomainException(description + " is required");
        }
        return normalized;
    }

    private static String optionalText(String value, int maximum, String description) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maximum) {
            throw new OaFormDomainException(description + " is too long");
        }
        return normalized;
    }

    private void touch() {
        updatedTime = LocalDateTime.now();
    }
}
