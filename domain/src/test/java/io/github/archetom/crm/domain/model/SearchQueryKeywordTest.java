package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
import io.github.archetom.crm.domain.policy.ReceivableFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearchQueryKeywordTest {

    private static final MemberId MEMBER = new MemberId(1L);
    private static final ContractVisibility CONTRACT_VISIBILITY = new ContractVisibility(
            DataScope.SELF, MEMBER, Set.of(), ContractAccessMode.READ);
    private static final InvoiceVisibility INVOICE_VISIBILITY = new InvoiceVisibility(
            DataScope.SELF, MEMBER, Set.of(), InvoiceAccessMode.READ);
    private static final ReceivableVisibility RECEIVABLE_VISIBILITY = new ReceivableVisibility(
            DataScope.SELF, MEMBER, Set.of(), ReceivableAccessMode.READ);
    private static final CustomerVisibility CUSTOMER_VISIBILITY = new CustomerVisibility(
            DataScope.SELF, MEMBER, Set.of(), CustomerAccessMode.READ);
    private static final ContactVisibility CONTACT_VISIBILITY = new ContactVisibility(
            DataScope.SELF, MEMBER, Set.of(), ContactAccessMode.READ);
    private static final LeadVisibility LEAD_VISIBILITY = new LeadVisibility(
            DataScope.SELF, MEMBER, Set.of(), LeadAccessMode.READ);
    private static final OpportunityVisibility OPPORTUNITY_VISIBILITY = new OpportunityVisibility(
            DataScope.SELF, MEMBER, Set.of(), OpportunityAccessMode.READ);
    private static final ReturnVisitVisibility RETURN_VISIT_VISIBILITY = new ReturnVisitVisibility(
            DataScope.SELF, MEMBER, Set.of(), ReturnVisitAccessMode.READ);

    @Test
    void acceptsAndTrimsTwoHundredSupplementaryCodePointsInEverySearchQuery() {
        String keyword = " " + "\uD83D\uDE80".repeat(SearchKeyword.MAX_CODE_POINTS) + " ";

        searchQueries().forEach(factory -> assertEquals(keyword.trim(), factory.apply(keyword)));
    }

    @Test
    void rejectsTwoHundredAndOneSupplementaryCodePointsInEverySearchQuery() {
        String keyword = "\uD83D\uDE80".repeat(SearchKeyword.MAX_CODE_POINTS + 1);

        searchQueries().forEach(factory -> assertThrows(IllegalArgumentException.class,
                () -> factory.apply(keyword)));
    }

    @Test
    void remainingQueriesRejectCandidatesOutsideTheirFixedCatalogs() {
        Map<FieldCode, SearchMatchMode> invalid = Map.of(
                new FieldCode(CustomerFieldCatalog.NAME), SearchMatchMode.LITERAL_PREFIX);

        assertThrows(RuntimeException.class, () -> new ContractQuery(
                CONTRACT_VISIBILITY, "keyword", invalid, null,
                null, null, null, null, null, 1, 20));
        assertThrows(RuntimeException.class, () -> new InvoiceQuery(
                INVOICE_VISIBILITY, "keyword", invalid, null,
                null, null, null, null, InvoiceSort.from(null, null), 1, 20));
        assertThrows(RuntimeException.class, () -> new ReceivableQuery(
                1, 20, "keyword", invalid, null,
                null, null, null, null, ReceivableSort.from(null, null), RECEIVABLE_VISIBILITY));
        assertThrows(RuntimeException.class, () -> new ReceivablePlanQuery(
                1, 20, "keyword", Map.of(
                new FieldCode(ReceivableFieldCatalog.NUMBER), SearchMatchMode.LITERAL_PREFIX),
                null, null, null, RECEIVABLE_VISIBILITY));
    }

    @Test
    void ownerAndSubordinateOwnerScenesAreMutuallyExclusive() {
        MemberId subordinate = new MemberId(2L);

        assertThrows(RuntimeException.class, () -> new ContractQuery(CONTRACT_VISIBILITY,
                null, Map.of(), MEMBER, Set.of(subordinate), null, null, null, null, 1, 20));
        assertThrows(RuntimeException.class, () -> new InvoiceQuery(INVOICE_VISIBILITY,
                null, Map.of(), MEMBER, Set.of(subordinate), null, null, null,
                InvoiceSort.from(null, null), 1, 20));
        assertThrows(RuntimeException.class, () -> new ReceivableQuery(1, 20,
                null, Map.of(), MEMBER, Set.of(subordinate), null, null, null,
                ReceivableSort.from(null, null), RECEIVABLE_VISIBILITY));
        assertThrows(RuntimeException.class, () -> new ReceivablePlanQuery(1, 20,
                null, Map.of(), MEMBER, Set.of(subordinate), null, null,
                RECEIVABLE_VISIBILITY));
        assertThrows(RuntimeException.class, () -> new ReturnVisitQuery(1, 20,
                null, Map.of(), MEMBER, Set.of(subordinate), null, null,
                ReturnVisitSort.from(null, null), RETURN_VISIT_VISIBILITY));
    }

    private static Set<Function<String, String>> searchQueries() {
        return Set.of(
                keyword -> new ContractQuery(CONTRACT_VISIBILITY, keyword, Map.of(), null,
                        null, null, null, null, null, 1, 20).keyword(),
                keyword -> new InvoiceQuery(INVOICE_VISIBILITY, keyword, Map.of(), null,
                        null, null, null, null, InvoiceSort.from(null, null), 1, 20).keyword(),
                keyword -> new ReceivableQuery(1, 20, keyword, Map.of(), null,
                        null, null, null, null, ReceivableSort.from(null, null), RECEIVABLE_VISIBILITY).keyword(),
                keyword -> new ReceivablePlanQuery(1, 20, keyword, Map.of(), null,
                        null, null, RECEIVABLE_VISIBILITY).keyword(),
                keyword -> new CustomerQuery(CUSTOMER_VISIBILITY, keyword, Map.of(), null,
                        null, null, null, null, List.of(), 1, 20).keyword(),
                keyword -> new ContactQuery(CONTACT_VISIBILITY, keyword, Map.of(), null,
                        null, null, null, null, null, List.of(), 1, 20).keyword(),
                keyword -> new LeadQuery(LEAD_VISIBILITY, keyword, Map.of(), null,
                        null, null, null, null, null, List.of(), 1, 20).keyword(),
                keyword -> new OpportunityQuery(OPPORTUNITY_VISIBILITY, keyword, Map.of(), null,
                        null, null, null, null, null, List.of(), 1, 20).keyword(),
                keyword -> new ProductQuery(keyword, Map.of(), null, null, null,
                        null, null, null, List.of(), 1, 20).keyword(),
                keyword -> new ReturnVisitQuery(1, 20, keyword, Map.of(), null,
                        null, null, null, ReturnVisitSort.from(null, null), RETURN_VISIT_VISIBILITY).keyword());
    }
}
