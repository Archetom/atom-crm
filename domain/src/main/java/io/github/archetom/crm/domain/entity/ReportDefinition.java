package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.CustomizationDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.ReportDefinitionStatus;
import io.github.archetom.crm.domain.model.ReportGranularity;
import io.github.archetom.crm.domain.model.ReportMeasure;
import io.github.archetom.crm.domain.model.ReportMeasureType;
import io.github.archetom.crm.domain.policy.IdentifierKeyNormalizer;
import io.github.archetom.crm.domain.policy.SavedViewPolicy;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ReportDefinitionId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Current-member-owned, single-source and deliberately non-executable report metadata. */
@Getter
public class ReportDefinition extends AggregateRoot<ReportDefinitionId> {

    public static final int MAX_FILTERS = 20;
    public static final int MAX_MEASURES = 8;

    private ReportDefinitionId id;
    private TenantId tenantId;
    private MemberId creatorMemberId;
    private String name;
    private String normalizedName;
    private String description;
    private BusinessObjectType objectType;
    private FieldCode timeFieldCode;
    private ReportGranularity granularity;
    private FieldCode dimensionFieldCode;
    private List<FilterCondition> filters = List.of();
    private List<ReportMeasure> measures = List.of();
    private ReportDefinitionStatus status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private ReportDefinition() {
    }

    public record Snapshot(
            ReportDefinitionId id, TenantId tenantId, MemberId creatorMemberId,
            String name, String normalizedName, String description,
            BusinessObjectType objectType, FieldCode timeFieldCode,
            ReportGranularity granularity, FieldCode dimensionFieldCode,
            List<FilterCondition> filters, List<ReportMeasure> measures,
            ReportDefinitionStatus status, Long version,
            LocalDateTime createdTime, LocalDateTime updatedTime) {
    }

    public static ReportDefinition create(
            TenantId tenantId, MemberId creatorMemberId, String name, String description,
            BusinessObjectType objectType, FieldCode timeFieldCode,
            ReportGranularity granularity, FieldCode dimensionFieldCode,
            Collection<FilterCondition> filters, Collection<ReportMeasure> measures,
            Map<FieldCode, CustomFieldType> searchableFields) {
        ReportDefinition definition = new ReportDefinition();
        definition.tenantId = require(tenantId, "report tenant");
        definition.creatorMemberId = require(creatorMemberId, "report creator");
        definition.objectType = requireSupportedObject(objectType);
        definition.status = ReportDefinitionStatus.ACTIVE;
        definition.apply(name, description, timeFieldCode, granularity, dimensionFieldCode,
                filters, measures, searchableFields);
        definition.createdTime = definition.updatedTime;
        return definition;
    }

    public static ReportDefinition reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new CustomizationDomainException("report definition snapshot is required");
        }
        ReportDefinition definition = new ReportDefinition();
        definition.id = require(snapshot.id(), "report ID");
        definition.tenantId = require(snapshot.tenantId(), "report tenant");
        definition.creatorMemberId = require(snapshot.creatorMemberId(), "report creator");
        definition.name = requireText(snapshot.name(), 100, "report name");
        definition.normalizedName = IdentifierKeyNormalizer.normalize(definition.name);
        if (snapshot.normalizedName() == null
                || !definition.normalizedName.equals(snapshot.normalizedName())) {
            throw new CustomizationDomainException("stored report normalized name is invalid");
        }
        definition.description = optionalText(snapshot.description(), 500, "report description");
        definition.objectType = requireSupportedObject(snapshot.objectType());
        definition.timeFieldCode = require(snapshot.timeFieldCode(), "report time field");
        definition.granularity = require(snapshot.granularity(), "report granularity");
        definition.dimensionFieldCode = snapshot.dimensionFieldCode();
        definition.filters = copyFilters(snapshot.filters());
        definition.measures = copyMeasures(snapshot.measures());
        validateShape(definition.filters, definition.measures);
        definition.status = require(snapshot.status(), "report status");
        definition.restoreVersion(snapshot.version());
        definition.createdTime = snapshot.createdTime();
        definition.updatedTime = snapshot.updatedTime();
        return definition;
    }

    public void update(
            String name, String description, FieldCode timeFieldCode,
            ReportGranularity granularity, FieldCode dimensionFieldCode,
            Collection<FilterCondition> filters, Collection<ReportMeasure> measures,
            Map<FieldCode, CustomFieldType> searchableFields) {
        ensureActive();
        apply(name, description, timeFieldCode, granularity, dimensionFieldCode,
                filters, measures, searchableFields);
    }

    public void delete() {
        ensureActive();
        status = ReportDefinitionStatus.DELETED;
        updatedTime = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == ReportDefinitionStatus.ACTIVE;
    }

    public Set<FieldCode> referencedFields() {
        Set<FieldCode> result = new HashSet<>();
        result.add(timeFieldCode);
        if (dimensionFieldCode != null) result.add(dimensionFieldCode);
        filters.forEach(value -> result.add(value.fieldCode()));
        measures.stream().map(ReportMeasure::fieldCode)
                .filter(java.util.Objects::nonNull).forEach(result::add);
        return Set.copyOf(result);
    }

    public void onPersisted(ReportDefinitionId persistedId, Long persistedVersion,
                            LocalDateTime persistedCreated, LocalDateTime persistedUpdated) {
        if (persistedId == null || id != null && !id.sameValueAs(persistedId)) {
            throw new CustomizationDomainException("persisted report ID is invalid");
        }
        id = persistedId;
        restoreVersion(persistedVersion);
        if (persistedCreated != null) createdTime = persistedCreated;
        if (persistedUpdated != null) updatedTime = persistedUpdated;
    }

    private void apply(
            String nextName, String nextDescription, FieldCode nextTimeField,
            ReportGranularity nextGranularity, FieldCode nextDimension,
            Collection<FilterCondition> nextFilters,
            Collection<ReportMeasure> nextMeasures,
            Map<FieldCode, CustomFieldType> searchableFields) {
        String validatedName = requireText(nextName, 100, "report name");
        FieldCode validatedTime = require(nextTimeField, "report time field");
        ReportGranularity validatedGranularity = require(nextGranularity, "report granularity");
        List<FilterCondition> validatedFilters = copyFilters(nextFilters);
        List<ReportMeasure> validatedMeasures = copyMeasures(nextMeasures);
        validateShape(validatedFilters, validatedMeasures);
        if (searchableFields == null) {
            throw new CustomizationDomainException("report field catalog is required");
        }
        if (validatedFilters.stream().anyMatch(filter ->
                searchableFields.get(filter.fieldCode()) == CustomFieldType.RELATION)) {
            throw new CustomizationDomainException("report relation filters are unsupported");
        }
        SavedViewPolicy.validate(validatedFilters, searchableFields);
        CustomFieldType timeType = searchableFields.get(validatedTime);
        if (timeType != CustomFieldType.DATE && timeType != CustomFieldType.DATETIME) {
            throw new CustomizationDomainException("report time field is invalid");
        }
        if (nextDimension != null && !Set.of(CustomFieldType.SELECT, CustomFieldType.BOOLEAN,
                CustomFieldType.USER, CustomFieldType.DEPARTMENT)
                .contains(searchableFields.get(nextDimension))) {
            throw new CustomizationDomainException("report dimension field is invalid");
        }
        for (ReportMeasure measure : validatedMeasures) {
            if (measure.type() == ReportMeasureType.SUM
                    && !Set.of(CustomFieldType.INTEGER, CustomFieldType.DECIMAL)
                    .contains(searchableFields.get(measure.fieldCode()))) {
                throw new CustomizationDomainException("report SUM field is invalid");
            }
        }
        name = validatedName;
        normalizedName = IdentifierKeyNormalizer.normalize(validatedName);
        description = optionalText(nextDescription, 500, "report description");
        timeFieldCode = validatedTime;
        granularity = validatedGranularity;
        dimensionFieldCode = nextDimension;
        filters = validatedFilters;
        measures = validatedMeasures;
        updatedTime = LocalDateTime.now();
    }

    private static void validateShape(List<FilterCondition> filters, List<ReportMeasure> measures) {
        Set<FieldCode> filterFields = new HashSet<>();
        if (filters.size() > MAX_FILTERS || filters.stream().anyMatch(java.util.Objects::isNull)
                || filters.stream().anyMatch(value -> !filterFields.add(value.fieldCode()))) {
            throw new CustomizationDomainException("report filters are invalid");
        }
        if (measures.isEmpty() || measures.size() > MAX_MEASURES
                || measures.stream().anyMatch(java.util.Objects::isNull)
                || measures.size() != new HashSet<>(measures).size()) {
            throw new CustomizationDomainException("report measures are invalid");
        }
    }

    private static List<FilterCondition> copyFilters(Collection<FilterCondition> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static List<ReportMeasure> copyMeasures(Collection<ReportMeasure> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static BusinessObjectType requireSupportedObject(BusinessObjectType value) {
        if (value != BusinessObjectType.OPPORTUNITY && value != BusinessObjectType.CONTRACT
                && value != BusinessObjectType.RECEIVABLE) {
            throw new CustomizationDomainException("report source object is unsupported");
        }
        return value;
    }

    private void ensureActive() {
        if (!isActive()) throw new CustomizationDomainException("report definition is deleted");
    }

    private static String requireText(String value, int max, String description) {
        if (value == null || value.isBlank()) {
            throw new CustomizationDomainException(description + " is required");
        }
        String result = value.trim();
        if (result.codePointCount(0, result.length()) > max) {
            throw new CustomizationDomainException(description + " is too long");
        }
        return result;
    }

    private static String optionalText(String value, int max, String description) {
        return value == null || value.isBlank() ? null : requireText(value, max, description);
    }

    private static <T> T require(T value, String description) {
        if (value == null) throw new CustomizationDomainException(description + " is required");
        return value;
    }
}
