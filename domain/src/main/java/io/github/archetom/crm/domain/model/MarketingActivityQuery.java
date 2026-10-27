package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.MarketingActivityDomainException;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;

public record MarketingActivityQuery(
        String keyword,
        MarketingActivityRelatedObjectType relatedObjectType,
        int page,
        int size) {
    public MarketingActivityQuery {
        if (page < 1 || size < 1 || size > 200) {
            throw new MarketingActivityDomainException("marketing activity query is invalid");
        }
        keyword = SearchKeyword.optional(keyword).map(SearchKeyword::value).orElse(null);
    }
}
