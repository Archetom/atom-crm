package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.CustomizationDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.policy.SavedViewPolicy;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.SavedViewId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Personal typed filter definition within one verified tenant and object. */
@Getter
public class SavedView extends AggregateRoot<SavedViewId> {

    private SavedViewId id;
    private TenantId tenantId;
    private MemberId memberId;
    private BusinessObjectType objectType;
    private String name;
    private List<SavedViewCondition> savedConditions = List.of();
    private int sortOrder;
    private boolean hidden;
    private boolean defaultView;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private SavedView() {
    }

    public record Snapshot(SavedViewId id, TenantId tenantId, MemberId memberId,
                           BusinessObjectType objectType, String name,
                           List<SavedViewCondition> conditions, int sortOrder,
                           boolean hidden, boolean defaultView, Long version,
                           LocalDateTime createdTime, LocalDateTime updatedTime) {
    }

    public static SavedView create(TenantId tenantId, MemberId memberId,
                                   BusinessObjectType objectType, String name,
                                   Collection<FilterCondition> conditions, int sortOrder,
                                   boolean hidden,
                                   Map<FieldCode, CustomFieldType> searchableFields) {
        return createWithSavedConditions(tenantId, memberId, objectType, name,
                asSavedConditions(conditions), sortOrder, hidden, searchableFields);
    }

    public static SavedView createWithSavedConditions(TenantId tenantId, MemberId memberId,
                                                      BusinessObjectType objectType, String name,
                                                      Collection<SavedViewCondition> conditions,
                                                      int sortOrder, boolean hidden,
                                                      Map<FieldCode, CustomFieldType> searchableFields) {
        SavedView view = new SavedView();
        view.tenantId = requireTenant(tenantId);
        view.memberId = requireMember(memberId);
        view.objectType = requireObjectType(objectType);
        view.applySavedConditions(name, conditions, sortOrder, hidden, searchableFields);
        view.createdTime = view.updatedTime;
        return view;
    }

    public static SavedView reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new CustomizationDomainException("saved view snapshot must not be empty");
        }
        SavedView view = new SavedView();
        view.id = snapshot.id();
        view.tenantId = requireTenant(snapshot.tenantId());
        view.memberId = requireMember(snapshot.memberId());
        view.objectType = requireObjectType(snapshot.objectType());
        view.name = requireName(snapshot.name());
        view.savedConditions = snapshot.conditions() == null
                ? List.of() : List.copyOf(snapshot.conditions());
        view.sortOrder = requireOrder(snapshot.sortOrder());
        view.hidden = snapshot.hidden();
        view.defaultView = snapshot.defaultView();
        view.restoreVersion(snapshot.version());
        view.createdTime = snapshot.createdTime();
        view.updatedTime = snapshot.updatedTime();
        return view;
    }

    public void update(String name, Collection<FilterCondition> conditions, int sortOrder,
                       boolean hidden, Map<FieldCode, CustomFieldType> searchableFields) {
        updateSavedConditions(name, asSavedConditions(conditions), sortOrder, hidden, searchableFields);
    }

    public void updateSavedConditions(String name, Collection<SavedViewCondition> conditions,
                                      int sortOrder, boolean hidden,
                                      Map<FieldCode, CustomFieldType> searchableFields) {
        applySavedConditions(name, conditions, sortOrder, hidden, searchableFields);
    }

    public void markDefault() {
        defaultView = true;
        hidden = false;
        updatedTime = LocalDateTime.now();
    }

    public void clearDefault() {
        defaultView = false;
        updatedTime = LocalDateTime.now();
    }

    public boolean isApplicableTo(Map<FieldCode, CustomFieldType> searchableFields) {
        return SavedViewPolicy.isApplicable(getConditions(), searchableFields);
    }

    public void assertApplicableTo(Map<FieldCode, CustomFieldType> searchableFields) {
        SavedViewPolicy.validate(getConditions(), searchableFields);
    }

    /** Conditions used by repository queries; browser exposure never reaches those queries. */
    public List<FilterCondition> getConditions() {
        return savedConditions.stream().map(SavedViewCondition::filterCondition).toList();
    }

    public void onPersisted(SavedViewId id, Long version,
                            LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new CustomizationDomainException("persisted saved view ID is invalid");
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

    private void applySavedConditions(String name, Collection<SavedViewCondition> conditions,
                                      int sortOrder, boolean hidden,
                                      Map<FieldCode, CustomFieldType> searchableFields) {
        String nextName = requireName(name);
        List<SavedViewCondition> nextConditions = conditions == null ? List.of() : List.copyOf(conditions);
        int nextSortOrder = requireOrder(sortOrder);
        SavedViewPolicy.validate(filterConditions(nextConditions), searchableFields);

        this.name = nextName;
        this.savedConditions = nextConditions;
        this.sortOrder = nextSortOrder;
        this.hidden = hidden;
        if (hidden) {
            defaultView = false;
        }
        updatedTime = LocalDateTime.now();
    }

    private static List<SavedViewCondition> asSavedConditions(
            Collection<FilterCondition> conditions) {
        return (conditions == null ? List.<FilterCondition>of() : conditions).stream()
                .map(condition -> new SavedViewCondition(condition, false)).toList();
    }

    private static List<FilterCondition> filterConditions(
            Collection<SavedViewCondition> conditions) {
        return (conditions == null ? List.<SavedViewCondition>of() : conditions).stream()
                .map(condition -> {
                    if (condition == null) {
                        throw new CustomizationDomainException("saved view filter condition is required");
                    }
                    return condition.filterCondition();
                }).toList();
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 100) {
            throw new CustomizationDomainException("saved view name is invalid");
        }
        return value.trim();
    }

    private static int requireOrder(int value) {
        if (value < 0 || value > 10000) {
            throw new CustomizationDomainException("saved view order is invalid");
        }
        return value;
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new CustomizationDomainException("saved view tenant must not be empty");
        }
        return value;
    }

    private static MemberId requireMember(MemberId value) {
        if (value == null) {
            throw new CustomizationDomainException("saved view member must not be empty");
        }
        return value;
    }

    private static BusinessObjectType requireObjectType(BusinessObjectType value) {
        if (value == null) {
            throw new CustomizationDomainException("saved view object type must not be empty");
        }
        return value;
    }
}
