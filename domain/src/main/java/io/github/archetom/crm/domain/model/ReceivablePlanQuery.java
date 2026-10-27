package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ReceivableDomainException;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.policy.ReceivablePlanFieldCatalog;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ReceivablePlanQuery(
        long page, long size, String keyword,
        Map<FieldCode, SearchMatchMode> keywordCandidates, MemberId ownerMemberId,
        Set<MemberId> subordinateOwnerMemberIds,
        CustomerId customerId, ContractId contractId, List<FilterCondition> conditions,
        ReceivableVisibility visibility) {
    public ReceivablePlanQuery(
            long page, long size, String keyword,
            Map<FieldCode, SearchMatchMode> keywordCandidates, MemberId ownerMemberId,
            CustomerId customerId, ContractId contractId, ReceivableVisibility visibility) {
        this(page, size, keyword, keywordCandidates, ownerMemberId, null,
                customerId, contractId, List.of(), visibility);
    }

    public ReceivablePlanQuery(
            long page, long size, String keyword,
            Map<FieldCode, SearchMatchMode> keywordCandidates, MemberId ownerMemberId,
            Set<MemberId> subordinateOwnerMemberIds,
            CustomerId customerId, ContractId contractId, ReceivableVisibility visibility) {
        this(page, size, keyword, keywordCandidates, ownerMemberId, subordinateOwnerMemberIds,
                customerId, contractId, List.of(), visibility);
    }

    public ReceivablePlanQuery {
        if (page < 1 || size < 1 || size > 200 || visibility == null) {
            throw new ReceivableDomainException("receivable plan query is invalid");
        }
        keyword = SearchKeyword.optional(keyword).map(SearchKeyword::value).orElse(null);
        keywordCandidates = candidates(keyword, keywordCandidates);
        subordinateOwnerMemberIds = subordinateOwnerMemberIds == null ? null
                : Set.copyOf(subordinateOwnerMemberIds);
        if (ownerMemberId != null && subordinateOwnerMemberIds != null) {
            throw new ReceivableDomainException("receivable plan owner scenes are mutually exclusive");
        }
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    private static Map<FieldCode, SearchMatchMode> candidates(
            String keyword, Map<FieldCode, SearchMatchMode> values) {
        Map<FieldCode, SearchMatchMode> normalized = values == null ? Map.of() : Map.copyOf(values);
        if (keyword == null) {
            return Map.of();
        }
        if (!ReceivablePlanFieldCatalog.searchCandidates().entrySet()
                .containsAll(normalized.entrySet())) {
            throw new ReceivableDomainException("receivable plan keyword candidates are invalid");
        }
        return normalized;
    }
}
