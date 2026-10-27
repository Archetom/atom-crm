package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.exception.ContactDomainException;
import io.github.archetom.crm.domain.policy.ContactFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded contact list criteria; every dynamic filter was validated before construction. */
public record ContactQuery(
        ContactVisibility visibility,
        String keyword,
        Map<FieldCode, SearchMatchMode> keywordCandidates,
        String name,
        MemberId ownerMemberId,
        Set<MemberId> subordinateOwnerMemberIds,
        CustomerId customerId,
        Boolean starred,
        ContactSort sort,
        List<FilterCondition> conditions,
        int page,
        int size) {

    public ContactQuery {
        if (visibility == null || page < 1 || size < 1 || size > 200) {
            throw new ContactDomainException("contact query is invalid");
        }
        keyword = SearchKeyword.optional(keyword).map(SearchKeyword::value).orElse(null);
        keywordCandidates = candidates(keyword, keywordCandidates,
                ContactFieldCatalog.searchCandidates());
        name = normalize(name, 200);
        subordinateOwnerMemberIds = subordinateOwnerMemberIds == null ? null
                : Set.copyOf(subordinateOwnerMemberIds);
        if (ownerMemberId != null && subordinateOwnerMemberIds != null) {
            throw new ContactDomainException("contact owner scenes are mutually exclusive");
        }
        sort = sort == null ? ContactSort.from(null, null) : sort;
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
            throw new ContactDomainException("contact keyword candidates are invalid");
        }
        return normalized;
    }

    private static String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new ContactDomainException("contact name filter is too long");
        }
        return normalized;
    }
}
