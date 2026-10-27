package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.exception.OpportunityDomainException;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;

import java.util.List;
import java.util.Map;

/** Bounded opportunity list criteria; dynamic conditions are validated before construction. */
public record OpportunityQuery(
        OpportunityVisibility visibility,
        String keyword,
        Map<FieldCode, SearchMatchMode> keywordCandidates,
        String name,
        MemberId ownerMemberId,
        CustomerId customerId,
        OpportunityOutcome outcome,
        Boolean starred,
        OpportunitySort sort,
        List<FilterCondition> conditions,
        int page,
        int size) {

    public OpportunityQuery {
        if (visibility == null || page < 1 || size < 1 || size > 200) {
            throw new OpportunityDomainException("opportunity query is invalid");
        }
        keyword = SearchKeyword.optional(keyword).map(SearchKeyword::value).orElse(null);
        keywordCandidates = candidates(keyword, keywordCandidates,
                OpportunityFieldCatalog.searchCandidates());
        name = normalize(name);
        sort = sort == null ? OpportunitySort.from(null, null) : sort;
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
            throw new OpportunityDomainException("opportunity keyword candidates are invalid");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 200) {
            throw new OpportunityDomainException("opportunity name filter is too long");
        }
        return normalized;
    }
}
