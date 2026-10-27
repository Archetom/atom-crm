package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.ContractQuery;
import io.github.archetom.crm.domain.model.ContractVisibility;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.InvoiceAccessMode;
import io.github.archetom.crm.domain.model.InvoiceQuery;
import io.github.archetom.crm.domain.model.InvoiceSort;
import io.github.archetom.crm.domain.model.InvoiceVisibility;
import io.github.archetom.crm.domain.model.ReceivableAccessMode;
import io.github.archetom.crm.domain.model.ReceivablePlanQuery;
import io.github.archetom.crm.domain.model.ReceivableQuery;
import io.github.archetom.crm.domain.model.ReceivableSort;
import io.github.archetom.crm.domain.model.ReceivableVisibility;
import io.github.archetom.crm.domain.model.SearchMatchMode;
import io.github.archetom.crm.domain.policy.ContractFieldCatalog;
import io.github.archetom.crm.domain.policy.InvoiceFieldCatalog;
import io.github.archetom.crm.domain.policy.ReceivableFieldCatalog;
import io.github.archetom.crm.domain.policy.ReceivablePlanFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContractMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.InvoiceMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ReceivableMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ReceivablePlanMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.ContractPO;
import io.github.archetom.crm.infra.persistence.mysql.po.InvoicePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ReceivablePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ReceivablePlanPO;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RemainingBusinessKeywordSearchRepositoryTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId MEMBER = new MemberId(7L);

    @Test
    void contractNumberUsesOnlyTheNormalizedIdentifierKey() {
        ContractMapper mapper = mock(ContractMapper.class);
        AtomicReference<QueryWrapper<ContractPO>> captured = captureContractPage(mapper);
        ContractRepositoryImpl repository = new ContractRepositoryImpl(
                mapper, null, null, null, null, null, null, null, null);

        repository.findContracts(TENANT, new ContractQuery(contractVisibility(),
                " ＡＣＭＥ％＿ ", Map.of(code(ContractFieldCatalog.NUMBER),
                SearchMatchMode.NORMALIZED_PREFIX), null, null, null, null, null, null, 1, 20));

        String sql = captured.get().getCustomSqlSegment();
        assertTrue(sql.contains("contract_number_key LIKE"));
        assertFalse(sql.contains("name LIKE"));
        assertTrue(sql.contains("tenant_id"));
        assertTrue(sql.contains("status"));
        assertTrue(captured.get().getParamNameValuePairs().containsValue("acme!%!_"));
    }

    @Test
    void invoiceSearchUsesOnlyTheSelectedFixedField() {
        InvoiceMapper mapper = mock(InvoiceMapper.class);
        AtomicReference<QueryWrapper<InvoicePO>> captured = captureInvoicePage(mapper);
        InvoiceRepositoryImpl repository = new InvoiceRepositoryImpl(
                mapper, null, null, null, null, null);

        repository.findInvoices(TENANT, new InvoiceQuery(invoiceVisibility(),
                "Invoice%_", Map.of(code(InvoiceFieldCatalog.TITLE),
                SearchMatchMode.LITERAL_PREFIX), null, null, null, null, null,
                InvoiceSort.from(null, null), 1, 20));

        String sql = captured.get().getCustomSqlSegment();
        assertTrue(sql.contains("title LIKE"));
        assertFalse(sql.contains("application_number LIKE"));
        assertFalse(sql.contains("invoice_number LIKE"));
        assertTrue(captured.get().getParamNameValuePairs().containsValue("Invoice!%!_"));
    }

    @Test
    void receivableSearchNoLongerUsesPaymentMethod() {
        ReceivableMapper mapper = mock(ReceivableMapper.class);
        AtomicReference<QueryWrapper<ReceivablePO>> captured = captureReceivablePage(mapper);
        ReceivableRepositoryImpl repository = new ReceivableRepositoryImpl(
                mapper, null, null, null, null, null, null, null);

        repository.findReceivables(TENANT, new ReceivableQuery(1, 20, "REC%_",
                Map.of(code(ReceivableFieldCatalog.NUMBER), SearchMatchMode.LITERAL_PREFIX),
                null, null, null, null, null, ReceivableSort.from(null, null), receivableVisibility()));

        String sql = captured.get().getCustomSqlSegment();
        assertTrue(sql.contains("receipt_number LIKE"));
        assertFalse(sql.contains("payment_method LIKE"));
        assertTrue(captured.get().getParamNameValuePairs().containsValue("REC!%!_"));
    }

    @Test
    void receivablePlanUsesExactPositiveInstallmentAndInvalidTextMatchesNothing() {
        ReceivablePlanMapper mapper = mock(ReceivablePlanMapper.class);
        AtomicReference<QueryWrapper<ReceivablePlanPO>> captured = capturePlanPage(mapper);
        ReceivablePlanRepositoryImpl repository = new ReceivablePlanRepositoryImpl(
                mapper, null, null, null, null, null, null);
        Map<FieldCode, SearchMatchMode> candidate = Map.of(
                code(ReceivablePlanFieldCatalog.NUMBER), SearchMatchMode.EXACT);

        repository.findPlans(TENANT, new ReceivablePlanQuery(1, 20, "01", candidate,
                null, null, null, receivableVisibility()));

        String exactSql = captured.get().getCustomSqlSegment();
        assertTrue(exactSql.contains("installment"));
        assertFalse(exactSql.contains("LIKE"));
        assertTrue(captured.get().getParamNameValuePairs().containsValue(1));

        repository.findPlans(TENANT, new ReceivablePlanQuery(1, 20, "not-a-number",
                candidate, null, null, null, receivableVisibility()));

        assertTrue(captured.get().getCustomSqlSegment().contains("1 = 0"));
    }

    @Test
    void receivablePlanAppliesAuthorizedTypedConditionsToListAndSummaryPredicates() {
        ReceivablePlanMapper mapper = mock(ReceivablePlanMapper.class);
        AtomicReference<QueryWrapper<ReceivablePlanPO>> captured = capturePlanPage(mapper);
        AtomicReference<QueryWrapper<ReceivablePlanPO>> capturedSummary = new AtomicReference<>();
        when(mapper.selectMaps(any())).thenAnswer(invocation -> {
            capturedSummary.set(invocation.getArgument(0));
            return List.of();
        });
        ReceivablePlanRepositoryImpl repository = new ReceivablePlanRepositoryImpl(
                mapper, null, null, null, null, null, null);
        FilterCondition condition = new FilterCondition(
                code(ReceivablePlanFieldCatalog.PAYMENT_METHOD), FilterOperator.EQ,
                List.of("BANK_TRANSFER"));

        ReceivablePlanQuery query = new ReceivablePlanQuery(1, 20, null, Map.of(),
                null, null, null, null, List.of(condition), receivableVisibility());
        repository.findPlans(TENANT, query);
        repository.summarize(TENANT, query, receivableVisibility());

        String sql = captured.get().getCustomSqlSegment();
        assertTrue(sql.contains("payment_method"));
        assertTrue(sql.contains("tenant_id"));
        assertTrue(sql.contains("status"));
        assertTrue(captured.get().getParamNameValuePairs().containsValue("BANK_TRANSFER"));
        String summarySql = capturedSummary.get().getCustomSqlSegment();
        assertTrue(summarySql.contains("payment_method"));
        assertTrue(summarySql.contains("tenant_id"));
        assertTrue(summarySql.contains("status"));
        assertTrue(capturedSummary.get().getParamNameValuePairs().containsValue("BANK_TRANSFER"));
    }

    @Test
    void receivablePlanExportIntersectsReadAndExportOwnerScopes() {
        ReceivablePlanMapper mapper = mock(ReceivablePlanMapper.class);
        AtomicReference<QueryWrapper<ReceivablePlanPO>> captured = new AtomicReference<>();
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return List.of();
        });
        ReceivablePlanRepositoryImpl repository = new ReceivablePlanRepositoryImpl(
                mapper, null, null, null, null, null, null);
        ReceivablePlanQuery query = new ReceivablePlanQuery(1, 20, null, Map.of(),
                null, null, null, receivableVisibility());
        ReceivableVisibility exportScope = new ReceivableVisibility(
                DataScope.SELF, new MemberId(11L), Set.of(), ReceivableAccessMode.OWNER_SCOPE);

        repository.findExportPlans(TENANT, query, exportScope, 5_001);

        String sql = captured.get().getCustomSqlSegment();
        assertTrue(sql.contains("tenant_id"));
        assertTrue(sql.contains("status"));
        assertTrue(sql.contains("owner_actor_id"));
        assertTrue(captured.get().getParamNameValuePairs().containsValue(11L));
    }

    @Test
    void aKeywordWithoutPermittedRemainingCandidatesAddsAFalsePredicate() {
        ContractMapper mapper = mock(ContractMapper.class);
        AtomicReference<QueryWrapper<ContractPO>> captured = captureContractPage(mapper);
        ContractRepositoryImpl repository = new ContractRepositoryImpl(
                mapper, null, null, null, null, null, null, null, null);

        repository.findContracts(TENANT, new ContractQuery(contractVisibility(),
                "hidden", Map.of(), null, null, null, null, null, null, 1, 20));

        assertTrue(captured.get().getCustomSqlSegment().contains("1 = 0"));
    }

    @SuppressWarnings("unchecked")
    private AtomicReference<QueryWrapper<ContractPO>> captureContractPage(ContractMapper mapper) {
        AtomicReference<QueryWrapper<ContractPO>> captured = new AtomicReference<>();
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            captured.set((QueryWrapper<ContractPO>) invocation.getArgument(1));
            return invocation.getArgument(0);
        });
        return captured;
    }

    @SuppressWarnings("unchecked")
    private AtomicReference<QueryWrapper<InvoicePO>> captureInvoicePage(InvoiceMapper mapper) {
        AtomicReference<QueryWrapper<InvoicePO>> captured = new AtomicReference<>();
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            captured.set((QueryWrapper<InvoicePO>) invocation.getArgument(1));
            return invocation.getArgument(0);
        });
        return captured;
    }

    @SuppressWarnings("unchecked")
    private AtomicReference<QueryWrapper<ReceivablePO>> captureReceivablePage(
            ReceivableMapper mapper) {
        AtomicReference<QueryWrapper<ReceivablePO>> captured = new AtomicReference<>();
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            captured.set((QueryWrapper<ReceivablePO>) invocation.getArgument(1));
            return invocation.getArgument(0);
        });
        return captured;
    }

    @SuppressWarnings("unchecked")
    private AtomicReference<QueryWrapper<ReceivablePlanPO>> capturePlanPage(
            ReceivablePlanMapper mapper) {
        AtomicReference<QueryWrapper<ReceivablePlanPO>> captured = new AtomicReference<>();
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            captured.set((QueryWrapper<ReceivablePlanPO>) invocation.getArgument(1));
            return invocation.getArgument(0);
        });
        return captured;
    }

    private ContractVisibility contractVisibility() {
        return new ContractVisibility(DataScope.TENANT, MEMBER, Set.of(), ContractAccessMode.READ);
    }

    private InvoiceVisibility invoiceVisibility() {
        return new InvoiceVisibility(DataScope.TENANT, MEMBER, Set.of(), InvoiceAccessMode.READ);
    }

    private ReceivableVisibility receivableVisibility() {
        return new ReceivableVisibility(
                DataScope.TENANT, MEMBER, Set.of(), ReceivableAccessMode.READ);
    }

    private FieldCode code(String value) {
        return new FieldCode(value);
    }
}
