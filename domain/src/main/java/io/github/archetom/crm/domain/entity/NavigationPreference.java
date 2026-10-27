package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.AuthorizationDomainException;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.NavigationPreferenceId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Bounded member preference that can only hide or reorder code-defined navigation. */
@Getter
public class NavigationPreference extends AggregateRoot<NavigationPreferenceId> {

    public static final int MAX_CODES = 200;

    private NavigationPreferenceId id;
    private TenantId tenantId;
    private MemberId memberId;
    private List<String> orderedCodes = List.of();
    private Set<String> hiddenCodes = Set.of();
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private NavigationPreference() {
    }

    public record Snapshot(TenantId tenantId, MemberId memberId, List<String> orderedCodes,
                           Set<String> hiddenCodes, Long version, LocalDateTime createdTime,
                           LocalDateTime updatedTime) {
    }

    public static NavigationPreference create(TenantId tenantId, MemberId memberId,
                                              Collection<String> orderedCodes,
                                              Collection<String> hiddenCodes,
                                              Set<String> catalogCodes) {
        NavigationPreference preference = new NavigationPreference();
        preference.tenantId = requireTenant(tenantId);
        preference.memberId = requireMember(memberId);
        preference.id = new NavigationPreferenceId(tenantId, memberId);
        preference.change(orderedCodes, hiddenCodes, catalogCodes);
        preference.createdTime = preference.updatedTime;
        return preference;
    }

    public static NavigationPreference reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new AuthorizationDomainException("navigation preference snapshot must not be empty");
        }
        NavigationPreference preference = new NavigationPreference();
        preference.tenantId = requireTenant(snapshot.tenantId());
        preference.memberId = requireMember(snapshot.memberId());
        preference.id = new NavigationPreferenceId(preference.tenantId, preference.memberId);
        preference.orderedCodes = boundedStoredCodes(snapshot.orderedCodes());
        preference.hiddenCodes = Set.copyOf(boundedStoredCodes(snapshot.hiddenCodes()));
        preference.restoreVersion(snapshot.version());
        preference.createdTime = snapshot.createdTime();
        preference.updatedTime = snapshot.updatedTime();
        return preference;
    }

    public void change(Collection<String> orderedCodes, Collection<String> hiddenCodes,
                       Set<String> catalogCodes) {
        if (catalogCodes == null) {
            throw new AuthorizationDomainException("navigation catalog codes must not be empty");
        }
        List<String> ordered = validatedCodes(orderedCodes, catalogCodes);
        List<String> hidden = validatedCodes(hiddenCodes, catalogCodes);
        this.orderedCodes = ordered;
        this.hiddenCodes = Set.copyOf(hidden);
        updatedTime = LocalDateTime.now();
    }

    public List<String> applyTo(Collection<String> visibleCatalogCodes) {
        LinkedHashSet<String> remaining = new LinkedHashSet<>(immutableCodes(visibleCatalogCodes));
        remaining.removeAll(hiddenCodes);
        List<String> result = new java.util.ArrayList<>();
        for (String code : orderedCodes) {
            if (remaining.remove(code)) {
                result.add(code);
            }
        }
        result.addAll(remaining);
        return List.copyOf(result);
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

    private static List<String> validatedCodes(Collection<String> values, Set<String> catalogCodes) {
        if (values != null && values.size() > MAX_CODES) {
            throw new AuthorizationDomainException("navigation preferences exceed the code limit");
        }
        List<String> codes = immutableCodes(values);
        for (String code : codes) {
            if (!catalogCodes.contains(code)) {
                throw new AuthorizationDomainException("navigation preference contains an unknown code");
            }
        }
        return codes;
    }

    private static List<String> boundedStoredCodes(Collection<String> values) {
        if (values != null && values.size() > MAX_CODES) {
            throw new AuthorizationDomainException("stored navigation preferences exceed the code limit");
        }
        return immutableCodes(values);
    }

    private static List<String> immutableCodes(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank() || value.length() > 100) {
                throw new AuthorizationDomainException("navigation code is invalid");
            }
            unique.add(value);
        }
        return List.copyOf(unique);
    }

    private static TenantId requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new AuthorizationDomainException("navigation preference tenant must not be empty");
        }
        return tenantId;
    }

    private static MemberId requireMember(MemberId memberId) {
        if (memberId == null) {
            throw new AuthorizationDomainException("navigation preference member must not be empty");
        }
        return memberId;
    }
}
