package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ContractDomainException;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.policy.ContractFieldCatalog;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OpportunityId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ContractQuery(
        ContractVisibility visibility,
        String keyword,
        Map<FieldCode, SearchMatchMode> keywordCandidates,
        MemberId ownerMemberId,
        Set<MemberId> subordinateOwnerMemberIds,
        CustomerId customerId,
        OpportunityId opportunityId,
        ContractStatus status,
        ContractSort sort,
        List<FilterCondition> conditions,
        int page,
        int size) {
    public ContractQuery(
            ContractVisibility visibility, String keyword,
            Map<FieldCode, SearchMatchMode> keywordCandidates, MemberId ownerMemberId,
            Set<MemberId> subordinateOwnerMemberIds, CustomerId customerId,
            OpportunityId opportunityId, ContractStatus status, ContractSort sort,
            int page, int size) {
        this(visibility, keyword, keywordCandidates, ownerMemberId, subordinateOwnerMemberIds,
                customerId, opportunityId, status, sort, List.of(), page, size);
    }

    public ContractQuery {
        if (visibility == null || page < 1 || size < 1 || size > 200) {
            throw new ContractDomainException("contract query is invalid");
        }
        keyword = SearchKeyword.optional(keyword).map(SearchKeyword::value).orElse(null);
        keywordCandidates = candidates(keyword, keywordCandidates);
        subordinateOwnerMemberIds = subordinateOwnerMemberIds == null ? null
                : Set.copyOf(subordinateOwnerMemberIds);
        if (ownerMemberId != null && subordinateOwnerMemberIds != null) {
            throw new ContractDomainException("contract owner scenes are mutually exclusive");
        }
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        sort = sort == null ? ContractSort.from(null, null) : sort;
    }

    private static Map<FieldCode, SearchMatchMode> candidates(
            String keyword, Map<FieldCode, SearchMatchMode> values) {
        Map<FieldCode, SearchMatchMode> normalized = values == null ? Map.of() : Map.copyOf(values);
        if (keyword == null) {
            return Map.of();
        }
        if (!ContractFieldCatalog.searchCandidates().entrySet()
                .containsAll(normalized.entrySet())) {
            throw new ContractDomainException("contract keyword candidates are invalid");
        }
        return normalized;
    }
}
