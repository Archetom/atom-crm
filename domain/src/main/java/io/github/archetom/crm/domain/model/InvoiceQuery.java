package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.InvoiceDomainException;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.policy.InvoiceFieldCatalog;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record InvoiceQuery(
        InvoiceVisibility visibility,
        String keyword,
        Map<FieldCode, SearchMatchMode> keywordCandidates,
        MemberId ownerMemberId,
        Set<MemberId> subordinateOwnerMemberIds,
        CustomerId customerId,
        ContractId contractId,
        InvoiceStatus status,
        InvoiceSort sort,
        List<FilterCondition> conditions,
        int page,
        int size) {
    public InvoiceQuery(
            InvoiceVisibility visibility, String keyword,
            Map<FieldCode, SearchMatchMode> keywordCandidates, MemberId ownerMemberId,
            Set<MemberId> subordinateOwnerMemberIds, CustomerId customerId, ContractId contractId,
            InvoiceStatus status, InvoiceSort sort, int page, int size) {
        this(visibility, keyword, keywordCandidates, ownerMemberId, subordinateOwnerMemberIds,
                customerId, contractId, status, sort, List.of(), page, size);
    }

    public InvoiceQuery {
        if (visibility == null || sort == null || page < 1 || size < 1 || size > 200) {
            throw new InvoiceDomainException("invoice query is invalid");
        }
        keyword = SearchKeyword.optional(keyword).map(SearchKeyword::value).orElse(null);
        keywordCandidates = candidates(keyword, keywordCandidates);
        subordinateOwnerMemberIds = subordinateOwnerMemberIds == null ? null
                : Set.copyOf(subordinateOwnerMemberIds);
        if (ownerMemberId != null && subordinateOwnerMemberIds != null) {
            throw new InvoiceDomainException("invoice owner scenes are mutually exclusive");
        }
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    private static Map<FieldCode, SearchMatchMode> candidates(
            String keyword, Map<FieldCode, SearchMatchMode> values) {
        Map<FieldCode, SearchMatchMode> normalized = values == null ? Map.of() : Map.copyOf(values);
        if (keyword == null) {
            return Map.of();
        }
        if (!InvoiceFieldCatalog.searchCandidates().entrySet()
                .containsAll(normalized.entrySet())) {
            throw new InvoiceDomainException("invoice keyword candidates are invalid");
        }
        return normalized;
    }
}
