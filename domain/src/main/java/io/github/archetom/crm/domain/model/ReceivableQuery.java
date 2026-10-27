package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ReceivableDomainException;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.policy.ReceivableFieldCatalog;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ReceivablePlanId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ReceivableQuery(
        long page, long size, String keyword,
        Map<FieldCode, SearchMatchMode> keywordCandidates, MemberId ownerMemberId,
        Set<MemberId> subordinateOwnerMemberIds,
        CustomerId customerId, ContractId contractId, ReceivablePlanId planId,
        ReceivableSort sort, List<FilterCondition> conditions, ReceivableVisibility visibility) {
    public ReceivableQuery(
            long page, long size, String keyword,
            Map<FieldCode, SearchMatchMode> keywordCandidates, MemberId ownerMemberId,
            Set<MemberId> subordinateOwnerMemberIds, CustomerId customerId,
            ContractId contractId, ReceivablePlanId planId, ReceivableSort sort,
            ReceivableVisibility visibility) {
        this(page, size, keyword, keywordCandidates, ownerMemberId, subordinateOwnerMemberIds,
                customerId, contractId, planId, sort, List.of(), visibility);
    }

    public ReceivableQuery {
        if (page < 1 || size < 1 || size > 200 || sort == null || visibility == null) {
            throw new ReceivableDomainException("receivable query is invalid");
        }
        keyword = SearchKeyword.optional(keyword).map(SearchKeyword::value).orElse(null);
        keywordCandidates = candidates(keyword, keywordCandidates);
        subordinateOwnerMemberIds = subordinateOwnerMemberIds == null ? null
                : Set.copyOf(subordinateOwnerMemberIds);
        if (ownerMemberId != null && subordinateOwnerMemberIds != null) {
            throw new ReceivableDomainException("receivable owner scenes are mutually exclusive");
        }
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    private static Map<FieldCode, SearchMatchMode> candidates(
            String keyword, Map<FieldCode, SearchMatchMode> values) {
        Map<FieldCode, SearchMatchMode> normalized = values == null ? Map.of() : Map.copyOf(values);
        if (keyword == null) {
            return Map.of();
        }
        if (!ReceivableFieldCatalog.searchCandidates().entrySet()
                .containsAll(normalized.entrySet())) {
            throw new ReceivableDomainException("receivable keyword candidates are invalid");
        }
        return normalized;
    }
}
