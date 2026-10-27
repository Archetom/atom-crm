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

/** Bounded customer list criteria; every dynamic filter was validated before construction. */
public record CustomerQuery(
        CustomerVisibility visibility,
        String keyword,
        Map<FieldCode, SearchMatchMode> keywordCandidates,
        String name,
        MemberId ownerMemberId,
        Set<MemberId> subordinateOwnerMemberIds,
        Boolean starred,
        CustomerSort sort,
        List<FilterCondition> conditions,
        int page,
        int size) {

    public CustomerQuery {
        if (visibility == null || page < 1 || size < 1 || size > 200) {
            throw new CustomerDomainException("customer query is invalid");
        }
        keyword = SearchKeyword.optional(keyword).map(SearchKeyword::value).orElse(null);
        keywordCandidates = candidates(keyword, keywordCandidates,
                CustomerFieldCatalog.searchCandidates());
        name = normalize(name, 200);
        subordinateOwnerMemberIds = subordinateOwnerMemberIds == null ? null
                : Set.copyOf(subordinateOwnerMemberIds);
        if (ownerMemberId != null && subordinateOwnerMemberIds != null) {
            throw new CustomerDomainException("customer owner scenes are mutually exclusive");
        }
        sort = sort == null ? CustomerSort.from(null, null) : sort;
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    private static Map<FieldCode, SearchMatchMode> candidates(
            String keyword, Map<FieldCode, SearchMatchMode> values,
            Map<FieldCode, SearchMatchMode> catalog) {
        Map<FieldCode, SearchMatchMode> normalized = values == null ? Map.of() : Map.copyOf(values);
        if (keyword == null) {
            return Map.of();
        }
        if (!catalog.entrySet().containsAll(normalized.entrySet())) {
            throw new CustomerDomainException("customer keyword candidates are invalid");
        }
        return normalized;
    }

    private static String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new CustomerDomainException("customer name filter is too long");
        }
        return normalized;
    }
}
