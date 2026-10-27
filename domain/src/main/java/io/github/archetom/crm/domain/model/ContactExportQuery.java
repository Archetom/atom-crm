package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.exception.ContactDomainException;
import io.github.archetom.crm.domain.policy.ContactFieldCatalog;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;

import java.util.List;
import java.util.Map;

/** Validated Contact-only export predicate with separate read and owner-export visibility. */
public record ContactExportQuery(
        ContactVisibility readVisibility,
        ContactVisibility exportVisibility,
        String keyword,
        Map<FieldCode, SearchMatchMode> keywordCandidates,
        String name,
        MemberId ownerMemberId,
        CustomerId customerId,
        Boolean starred,
        List<FilterCondition> conditions) {

    public ContactExportQuery {
        if (readVisibility == null || exportVisibility == null
                || exportVisibility.accessMode() != ContactAccessMode.OWNER_SCOPE) {
            throw new ContactDomainException("contact export query is invalid");
        }
        keyword = SearchKeyword.optional(keyword).map(SearchKeyword::value).orElse(null);
        keywordCandidates = candidates(keyword, keywordCandidates);
        name = normalize(name);
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    private static Map<FieldCode, SearchMatchMode> candidates(
            String keyword, Map<FieldCode, SearchMatchMode> values) {
        Map<FieldCode, SearchMatchMode> normalized = values == null ? Map.of() : Map.copyOf(values);
        if (keyword == null) {
            return Map.of();
        }
        if (!ContactFieldCatalog.searchCandidates().entrySet().containsAll(normalized.entrySet())) {
            throw new ContactDomainException("contact export keyword candidates are invalid");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 200) {
            throw new ContactDomainException("contact export name filter is too long");
        }
        return normalized;
    }
}
