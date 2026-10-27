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

/** Validated Lead-only export predicate with separate read and owner-export visibility. */
public record LeadExportQuery(
        LeadVisibility readVisibility,
        LeadVisibility exportVisibility,
        String keyword,
        Map<FieldCode, SearchMatchMode> keywordCandidates,
        String name,
        MemberId ownerMemberId,
        Set<MemberId> subordinateOwnerMemberIds,
        Boolean starred,
        Boolean converted,
        List<FilterCondition> conditions) {

    public LeadExportQuery {
        if (readVisibility == null || exportVisibility == null
                || exportVisibility.accessMode() != LeadAccessMode.OWNER_SCOPE) {
            throw new LeadDomainException("lead export query is invalid");
        }
        keyword = SearchKeyword.optional(keyword).map(SearchKeyword::value).orElse(null);
        keywordCandidates = candidates(keyword, keywordCandidates);
        name = normalize(name);
        subordinateOwnerMemberIds = subordinateOwnerMemberIds == null ? null
                : Set.copyOf(subordinateOwnerMemberIds);
        if (ownerMemberId != null && subordinateOwnerMemberIds != null) {
            throw new LeadDomainException("lead export owner scenes are mutually exclusive");
        }
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    private static Map<FieldCode, SearchMatchMode> candidates(
            String keyword, Map<FieldCode, SearchMatchMode> values) {
        Map<FieldCode, SearchMatchMode> normalized = values == null ? Map.of() : Map.copyOf(values);
        if (keyword == null) {
            return Map.of();
        }
        if (!LeadFieldCatalog.searchCandidates().entrySet().containsAll(normalized.entrySet())) {
            throw new LeadDomainException("lead export keyword candidates are invalid");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 200) {
            throw new LeadDomainException("lead export name filter is too long");
        }
        return normalized;
    }
}
