package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.exception.ProductDomainException;
import io.github.archetom.crm.domain.policy.ProductFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ProductCategoryId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;

import java.util.List;
import java.util.Map;

/** Bounded tenant product query with validated typed conditions. */
public record ProductQuery(
        String keyword,
        Map<FieldCode, SearchMatchMode> keywordCandidates,
        String name,
        String code,
        ProductCategoryId categoryId,
        MemberId ownerMemberId,
        ProductAvailability availability,
        ProductSort sort,
        List<FilterCondition> conditions,
        int page,
        int size) {

    public ProductQuery {
        if (page < 1 || size < 1 || size > 200) {
            throw new ProductDomainException("product query is invalid");
        }
        keyword = SearchKeyword.optional(keyword).map(SearchKeyword::value).orElse(null);
        keywordCandidates = candidates(keyword, keywordCandidates,
                ProductFieldCatalog.searchCandidates());
        name = normalize(name, 200, "product name filter");
        code = normalize(code, 100, "product code filter");
        sort = sort == null ? ProductSort.from(null, null) : sort;
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
            throw new ProductDomainException("product keyword candidates are invalid");
        }
        return normalized;
    }

    private static String normalize(String value, int max, String description) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new ProductDomainException(description + " is too long");
        }
        return normalized;
    }
}
