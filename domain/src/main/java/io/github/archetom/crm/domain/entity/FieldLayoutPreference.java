package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.CustomizationDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FieldLayoutPreferenceId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Current-member list presentation preference, bounded to active fields in one object. */
@Getter
public class FieldLayoutPreference extends AggregateRoot<FieldLayoutPreferenceId> {

    public static final int MAX_FIELDS = 200;
    public static final int MIN_WIDTH = 80;
    public static final int MAX_WIDTH = 1000;

    private FieldLayoutPreferenceId id;
    private TenantId tenantId;
    private MemberId memberId;
    private BusinessObjectType objectType;
    private List<FieldCode> orderedCodes = List.of();
    private Set<FieldCode> hiddenCodes = Set.of();
    private Set<FieldCode> pinnedCodes = Set.of();
    private Map<FieldCode, Integer> columnWidths = Map.of();
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private FieldLayoutPreference() {
    }

    public record Snapshot(TenantId tenantId, MemberId memberId, BusinessObjectType objectType,
                           List<FieldCode> orderedCodes, Set<FieldCode> hiddenCodes,
                           Set<FieldCode> pinnedCodes, Map<FieldCode, Integer> columnWidths,
                           Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {
    }

    public static FieldLayoutPreference create(
            TenantId tenantId, MemberId memberId, BusinessObjectType objectType,
            Collection<FieldCode> orderedCodes, Collection<FieldCode> hiddenCodes,
            Collection<FieldCode> pinnedCodes, Map<FieldCode, Integer> columnWidths,
            Set<FieldCode> activeCodes) {
        FieldLayoutPreference preference = new FieldLayoutPreference();
        preference.tenantId = requireTenant(tenantId);
        preference.memberId = requireMember(memberId);
        preference.objectType = requireObjectType(objectType);
        preference.id = new FieldLayoutPreferenceId(tenantId, memberId, objectType);
        preference.change(orderedCodes, hiddenCodes, pinnedCodes, columnWidths, activeCodes);
        preference.createdTime = preference.updatedTime;
        return preference;
    }

    public static FieldLayoutPreference reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new CustomizationDomainException("field layout snapshot must not be empty");
        }
        FieldLayoutPreference preference = new FieldLayoutPreference();
        preference.tenantId = requireTenant(snapshot.tenantId());
        preference.memberId = requireMember(snapshot.memberId());
        preference.objectType = requireObjectType(snapshot.objectType());
        preference.id = new FieldLayoutPreferenceId(preference.tenantId, preference.memberId,
                preference.objectType);
        preference.orderedCodes = boundedCodes(snapshot.orderedCodes());
        preference.hiddenCodes = Set.copyOf(boundedCodes(snapshot.hiddenCodes()));
        preference.pinnedCodes = Set.copyOf(boundedCodes(snapshot.pinnedCodes()));
        preference.columnWidths = boundedWidths(snapshot.columnWidths());
        preference.ensureNoHiddenPinnedOverlap();
        preference.restoreVersion(snapshot.version());
        preference.createdTime = snapshot.createdTime();
        preference.updatedTime = snapshot.updatedTime();
        return preference;
    }

    public void change(Collection<FieldCode> orderedCodes, Collection<FieldCode> hiddenCodes,
                       Collection<FieldCode> pinnedCodes, Map<FieldCode, Integer> columnWidths,
                       Set<FieldCode> activeCodes) {
        if (activeCodes == null) {
            throw new CustomizationDomainException("active field catalog must not be empty");
        }
        List<FieldCode> nextOrderedCodes = validatedCodes(orderedCodes, activeCodes);
        Set<FieldCode> nextHiddenCodes = Set.copyOf(validatedCodes(hiddenCodes, activeCodes));
        Set<FieldCode> nextPinnedCodes = Set.copyOf(validatedCodes(pinnedCodes, activeCodes));
        Map<FieldCode, Integer> nextColumnWidths = validatedWidths(columnWidths, activeCodes);
        ensureNoHiddenPinnedOverlap(nextHiddenCodes, nextPinnedCodes);

        this.orderedCodes = nextOrderedCodes;
        this.hiddenCodes = nextHiddenCodes;
        this.pinnedCodes = nextPinnedCodes;
        this.columnWidths = nextColumnWidths;
        updatedTime = LocalDateTime.now();
    }

    public void onPersisted(Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {
        restoreVersion(version);
        if (createdTime != null) {
            this.createdTime = createdTime;
        }
        if (updatedTime != null) {
            this.updatedTime = updatedTime;
        }
    }

    private void ensureNoHiddenPinnedOverlap() {
        ensureNoHiddenPinnedOverlap(hiddenCodes, pinnedCodes);
    }

    private static void ensureNoHiddenPinnedOverlap(
            Set<FieldCode> hiddenCodes, Set<FieldCode> pinnedCodes) {
        if (pinnedCodes.stream().anyMatch(hiddenCodes::contains)) {
            throw new CustomizationDomainException("a field cannot be hidden and pinned");
        }
    }

    private static List<FieldCode> validatedCodes(Collection<FieldCode> values,
                                                  Set<FieldCode> activeCodes) {
        List<FieldCode> codes = boundedCodes(values);
        if (!activeCodes.containsAll(codes)) {
            throw new CustomizationDomainException("field layout contains an inactive or unknown field");
        }
        return codes;
    }

    private static List<FieldCode> boundedCodes(Collection<FieldCode> values) {
        if (values != null && values.size() > MAX_FIELDS) {
            throw new CustomizationDomainException("field layout exceeds the field limit");
        }
        LinkedHashSet<FieldCode> unique = new LinkedHashSet<>();
        if (values != null) {
            for (FieldCode value : values) {
                if (value == null || !unique.add(value)) {
                    throw new CustomizationDomainException("field layout codes must be non-null and unique");
                }
            }
        }
        return List.copyOf(unique);
    }

    private static Map<FieldCode, Integer> validatedWidths(Map<FieldCode, Integer> values,
                                                           Set<FieldCode> activeCodes) {
        Map<FieldCode, Integer> widths = boundedWidths(values);
        if (!activeCodes.containsAll(widths.keySet())) {
            throw new CustomizationDomainException("field widths contain an inactive or unknown field");
        }
        return widths;
    }

    private static Map<FieldCode, Integer> boundedWidths(Map<FieldCode, Integer> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        if (values.size() > MAX_FIELDS) {
            throw new CustomizationDomainException("field widths exceed the field limit");
        }
        Map<FieldCode, Integer> widths = new LinkedHashMap<>();
        values.forEach((code, width) -> {
            if (code == null || width == null || width < MIN_WIDTH || width > MAX_WIDTH) {
                throw new CustomizationDomainException("field width is invalid");
            }
            widths.put(code, width);
        });
        return Map.copyOf(widths);
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new CustomizationDomainException("field layout tenant must not be empty");
        }
        return value;
    }

    private static MemberId requireMember(MemberId value) {
        if (value == null) {
            throw new CustomizationDomainException("field layout member must not be empty");
        }
        return value;
    }

    private static BusinessObjectType requireObjectType(BusinessObjectType value) {
        if (value == null) {
            throw new CustomizationDomainException("field layout object type must not be empty");
        }
        return value;
    }
}
