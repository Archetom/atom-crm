package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.CustomizationDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldStatus;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FieldDefinitionId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Tenant-owned field metadata. Business values remain in their owning aggregate stores. */
@Getter
public class FieldDefinition extends AggregateRoot<FieldDefinitionId> {

    public static final int MAX_OPTIONS = 100;

    private FieldDefinitionId id;
    private TenantId tenantId;
    private BusinessObjectType objectType;
    private FieldCode code;
    private String displayName;
    private String description;
    private CustomFieldType fieldType;
    private boolean builtin;
    private boolean required;
    private boolean unique;
    private boolean searchable;
    private CustomFieldStatus status;
    private int displayOrder;
    private String sectionName;
    private List<FieldOption> options = List.of();
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private FieldDefinition() {
    }

    public record Snapshot(FieldDefinitionId id, TenantId tenantId, BusinessObjectType objectType,
                           FieldCode code, String displayName, String description,
                           CustomFieldType fieldType, boolean builtin, boolean required,
                           boolean unique, boolean searchable, CustomFieldStatus status,
                           int displayOrder, String sectionName, List<FieldOption> options,
                           Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {
    }

    public static FieldDefinition createCustom(
            TenantId tenantId, BusinessObjectType objectType, FieldCode code,
            String displayName, String description, CustomFieldType fieldType,
            boolean required, boolean unique, boolean searchable, int displayOrder,
            String sectionName, Collection<FieldOption> options) {
        return create(tenantId, objectType, code, displayName, description, fieldType, false,
                required, unique, searchable, displayOrder, sectionName, options);
    }

    public static FieldDefinition createBuiltin(
            TenantId tenantId, BusinessObjectType objectType, FieldCode code,
            String displayName, String description, CustomFieldType fieldType,
            boolean required, boolean unique, boolean searchable, int displayOrder,
            String sectionName, Collection<FieldOption> options) {
        return create(tenantId, objectType, code, displayName, description, fieldType, true,
                required, unique, searchable, displayOrder, sectionName, options);
    }

    private static FieldDefinition create(
            TenantId tenantId, BusinessObjectType objectType, FieldCode code,
            String displayName, String description, CustomFieldType fieldType, boolean builtin,
            boolean required, boolean unique, boolean searchable, int displayOrder,
            String sectionName, Collection<FieldOption> options) {
        FieldDefinition field = new FieldDefinition();
        field.tenantId = requireTenant(tenantId);
        field.objectType = requireObjectType(objectType);
        field.code = requireCode(code);
        field.fieldType = requireType(fieldType);
        field.builtin = builtin;
        field.status = CustomFieldStatus.ACTIVE;
        field.applyMetadata(displayName, description, required, unique, searchable,
                displayOrder, sectionName, options, false);
        field.createdTime = field.updatedTime;
        return field;
    }

    public static FieldDefinition reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new CustomizationDomainException("field definition snapshot must not be empty");
        }
        FieldDefinition field = new FieldDefinition();
        field.id = snapshot.id();
        field.tenantId = requireTenant(snapshot.tenantId());
        field.objectType = requireObjectType(snapshot.objectType());
        field.code = requireCode(snapshot.code());
        field.fieldType = requireType(snapshot.fieldType());
        field.builtin = snapshot.builtin();
        field.status = snapshot.status() == null ? CustomFieldStatus.INACTIVE : snapshot.status();
        field.displayName = requireName(snapshot.displayName());
        field.description = normalize(snapshot.description(), 500, "field description");
        field.required = snapshot.required();
        field.unique = validateUnique(field.fieldType, snapshot.unique());
        field.searchable = validateSearchable(field.fieldType, snapshot.searchable());
        field.displayOrder = requireOrder(snapshot.displayOrder());
        field.sectionName = normalize(snapshot.sectionName(), 100, "field section");
        field.options = validateOptions(field.fieldType, snapshot.options());
        field.restoreVersion(snapshot.version());
        field.createdTime = snapshot.createdTime();
        field.updatedTime = snapshot.updatedTime();
        return field;
    }

    public void update(String displayName, String description, boolean required, boolean unique,
                       boolean searchable, int displayOrder, String sectionName,
                       Collection<FieldOption> options) {
        ensureCustom();
        if (unique != this.unique) {
            throw new CustomizationDomainException(
                    "field uniqueness cannot be changed after creation");
        }
        applyMetadata(displayName, description, required, unique, searchable,
                displayOrder, sectionName, options, true);
    }

    public void deactivate() {
        ensureCustom();
        status = CustomFieldStatus.INACTIVE;
        updatedTime = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == CustomFieldStatus.ACTIVE;
    }

    public void onPersisted(FieldDefinitionId id, Long version,
                            LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new CustomizationDomainException("persisted field ID is invalid");
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

    private void applyMetadata(String displayName, String description, boolean required,
                               boolean unique, boolean searchable, int displayOrder,
                               String sectionName, Collection<FieldOption> options,
                               boolean preserveOptionValues) {
        String nextDisplayName = requireName(displayName);
        String nextDescription = normalize(description, 500, "field description");
        boolean nextUnique = validateUnique(fieldType, unique);
        int nextDisplayOrder = requireOrder(displayOrder);
        String nextSectionName = normalize(sectionName, 100, "field section");
        List<FieldOption> nextOptions = validateOptions(fieldType, options);
        if (preserveOptionValues) {
            Set<String> newValues = nextOptions.stream().map(FieldOption::value).collect(Collectors.toSet());
            if (this.options.stream().map(FieldOption::value).anyMatch(value -> !newValues.contains(value))) {
                throw new CustomizationDomainException("existing field option values cannot be removed");
            }
        }

        this.displayName = nextDisplayName;
        this.description = nextDescription;
        this.required = required;
        this.unique = nextUnique;
        this.searchable = validateSearchable(fieldType, searchable);
        this.displayOrder = nextDisplayOrder;
        this.sectionName = nextSectionName;
        this.options = nextOptions;
        this.updatedTime = LocalDateTime.now();
    }

    private void ensureCustom() {
        if (builtin) {
            throw new CustomizationDomainException("built-in fields are immutable");
        }
    }

    private static List<FieldOption> validateOptions(CustomFieldType type,
                                                     Collection<FieldOption> options) {
        if (options != null && options.size() > MAX_OPTIONS) {
            throw new CustomizationDomainException("field options exceed the supported limit");
        }
        Map<String, FieldOption> unique = new LinkedHashMap<>();
        if (options != null) {
            for (FieldOption option : options) {
                if (option == null || unique.putIfAbsent(option.value(), option) != null) {
                    throw new CustomizationDomainException("field option values must be unique");
                }
            }
        }
        if (type.requiresOptions() && unique.isEmpty()) {
            throw new CustomizationDomainException("select fields require options");
        }
        if (!type.requiresOptions() && !unique.isEmpty()) {
            throw new CustomizationDomainException("this field type does not support options");
        }
        return List.copyOf(unique.values());
    }

    private static boolean validateUnique(CustomFieldType type, boolean unique) {
        if (type == CustomFieldType.FILE && unique) {
            throw new CustomizationDomainException("file fields cannot be unique");
        }
        if (unique && !type.supportsUniqueValues()) {
            throw new CustomizationDomainException("this field type cannot be unique");
        }
        return unique;
    }

    private static boolean validateSearchable(CustomFieldType type, boolean searchable) {
        if (type == CustomFieldType.FILE && searchable) {
            throw new CustomizationDomainException("file fields cannot be searchable");
        }
        return searchable;
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 100) {
            throw new CustomizationDomainException("field display name is invalid");
        }
        return value.trim();
    }

    private static String normalize(String value, int maxLength, String description) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new CustomizationDomainException(description + " is too long");
        }
        return normalized;
    }

    private static int requireOrder(int order) {
        if (order < 0 || order > 10000) {
            throw new CustomizationDomainException("field display order is invalid");
        }
        return order;
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new CustomizationDomainException("field tenant must not be empty");
        }
        return value;
    }

    private static BusinessObjectType requireObjectType(BusinessObjectType value) {
        if (value == null) {
            throw new CustomizationDomainException("field object type must not be empty");
        }
        return value;
    }

    private static FieldCode requireCode(FieldCode value) {
        if (value == null) {
            throw new CustomizationDomainException("field code must not be empty");
        }
        return value;
    }

    private static CustomFieldType requireType(CustomFieldType value) {
        if (value == null) {
            throw new CustomizationDomainException("field type must not be empty");
        }
        return value;
    }
}
