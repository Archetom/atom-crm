package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import io.github.archetom.crm.domain.policy.ReturnVisitFieldCatalog;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ReturnVisitQuery(long page, long size, String keyword,
                               Map<FieldCode, SearchMatchMode> keywordCandidates,
                               MemberId ownerMemberId,
                               Set<MemberId> subordinateOwnerMemberIds,
                               CustomerId customerId, ContractId contractId,
                               ReturnVisitSort sort,
                               List<FilterCondition> conditions,
                               ReturnVisitVisibility visibility) {
    public ReturnVisitQuery(
            long page, long size, String keyword,
            Map<FieldCode, SearchMatchMode> keywordCandidates, MemberId ownerMemberId,
            Set<MemberId> subordinateOwnerMemberIds, CustomerId customerId,
            ContractId contractId, ReturnVisitSort sort, ReturnVisitVisibility visibility) {
        this(page, size, keyword, keywordCandidates, ownerMemberId, subordinateOwnerMemberIds,
                customerId, contractId, sort, List.of(), visibility);
    }

    public ReturnVisitQuery {
        if (page < 1 || size < 1 || size > 200 || sort == null || visibility == null) {
            throw new IllegalArgumentException("return visit query is invalid");
        }
        keyword = SearchKeyword.optional(keyword).map(SearchKeyword::value).orElse(null);
        keywordCandidates = keywordCandidates == null ? Map.of() : Map.copyOf(keywordCandidates);
        subordinateOwnerMemberIds = subordinateOwnerMemberIds == null ? null
                : Set.copyOf(subordinateOwnerMemberIds);
        if (ownerMemberId != null && subordinateOwnerMemberIds != null) {
            throw new IllegalArgumentException("return visit owner scenes are mutually exclusive");
        }
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        if (keyword == null) {
            keywordCandidates = Map.of();
        } else if (!ReturnVisitFieldCatalog.searchCandidates().entrySet()
                .containsAll(keywordCandidates.entrySet())) {
            throw new IllegalArgumentException("return visit keyword candidates are invalid");
        }
    }
}
