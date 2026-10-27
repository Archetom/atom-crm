package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.exception.CustomerDomainException;
import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validated, Customer-only export predicate. Read visibility and export visibility remain
 * separate so persistence can intersect both predicates without widening collaborator access.
 */
public record CustomerExportQuery(
        CustomerVisibility readVisibility,
        CustomerVisibility exportVisibility,
        String keyword,
        Map<FieldCode, SearchMatchMode> keywordCandidates,
        String name,
        MemberId ownerMemberId,
        Set<MemberId> subordinateOwnerMemberIds,
        Boolean starred,
        List<FilterCondition> conditions) {

    public CustomerExportQuery {
        if (readVisibility == null || exportVisibility == null
                || exportVisibility.accessMode() != CustomerAccessMode.OWNER_SCOPE) {
            throw new CustomerDomainException("customer export query is invalid");
        }
        keyword = SearchKeyword.optional(keyword).map(SearchKeyword::value).orElse(null);
        keywordCandidates = candidates(keyword, keywordCandidates);
        name = normalize(name);
        subordinateOwnerMemberIds = subordinateOwnerMemberIds == null ? null
                : Set.copyOf(subordinateOwnerMemberIds);
        if (ownerMemberId != null && subordinateOwnerMemberIds != null) {
            throw new CustomerDomainException("customer export owner scenes are mutually exclusive");
        }
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    private static Map<FieldCode, SearchMatchMode> candidates(
            String keyword, Map<FieldCode, SearchMatchMode> values) {
        Map<FieldCode, SearchMatchMode> normalized = values == null ? Map.of() : Map.copyOf(values);
        if (keyword == null) {
            return Map.of();
        }
        if (!CustomerFieldCatalog.searchCandidates().entrySet().containsAll(normalized.entrySet())) {
            throw new CustomerDomainException("customer export keyword candidates are invalid");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 200) {
            throw new CustomerDomainException("customer export name filter is too long");
        }
        return normalized;
    }
}
