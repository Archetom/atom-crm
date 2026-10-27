package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.exception.LeadDomainException;
import io.github.archetom.crm.domain.policy.LeadFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded lead list criteria; every dynamic filter was validated before construction. */
public record LeadQuery(
        LeadVisibility visibility,
        String keyword,
        Map<FieldCode, SearchMatchMode> keywordCandidates,
        String name,
        MemberId ownerMemberId,
        Set<MemberId> subordinateOwnerMemberIds,
        Boolean starred,
        Boolean converted,
        LeadSort sort,
        List<FilterCondition> conditions,
        int page,
        int size) {

    public LeadQuery {
        if (visibility == null || page < 1 || size < 1 || size > 200) {
            throw new LeadDomainException("lead query is invalid");
        }
        keyword = SearchKeyword.optional(keyword).map(SearchKeyword::value).orElse(null);
        keywordCandidates = candidates(keyword, keywordCandidates,
                LeadFieldCatalog.searchCandidates());
        name = normalize(name, 200);
        subordinateOwnerMemberIds = subordinateOwnerMemberIds == null ? null
                : Set.copyOf(subordinateOwnerMemberIds);
        if (ownerMemberId != null && subordinateOwnerMemberIds != null) {
            throw new LeadDomainException("lead owner scenes are mutually exclusive");
        }
        sort = sort == null ? LeadSort.from(null, null) : sort;
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
            throw new LeadDomainException("lead keyword candidates are invalid");
        }
        return normalized;
    }

    private static String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new LeadDomainException("lead name filter is too long");
        }
        return normalized;
    }
}
