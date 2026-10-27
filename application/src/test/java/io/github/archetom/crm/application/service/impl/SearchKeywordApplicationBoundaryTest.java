package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.dto.request.ContractQueryRequest;
import io.github.archetom.crm.api.dto.request.InvoiceQueryRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanQueryRequest;
import io.github.archetom.crm.api.dto.request.ReceivableQueryRequest;
import io.github.archetom.crm.application.security.ContractAccessResolver;
import io.github.archetom.crm.application.security.ContractAccessResolver.ContractAccess;
import io.github.archetom.crm.application.security.InvoiceAccessResolver;
import io.github.archetom.crm.application.security.InvoiceAccessResolver.InvoiceAccess;
import io.github.archetom.crm.application.security.ReceivableAccessResolver;
import io.github.archetom.crm.application.security.ReceivableAccessResolver.ReceivableAccess;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.ContractVisibility;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.InvoiceAccessMode;
import io.github.archetom.crm.domain.model.InvoiceVisibility;
import io.github.archetom.crm.domain.model.ReceivableAccessMode;
import io.github.archetom.crm.domain.model.ReceivableVisibility;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Result;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchKeywordApplicationBoundaryTest {

    private static final TenantId TENANT = new TenantId(1L);
    private static final MemberId MEMBER = new MemberId(2L);
    private static final String OVER_LIMIT_KEYWORD =
            "\uD83D\uDE80".repeat(SearchKeyword.MAX_CODE_POINTS + 1);

    @Test
    void accessDenialTakesPrecedenceOverAnOverLimitKeyword() {
        ContractAccessResolver contractAccess = mock(ContractAccessResolver.class);
        InvoiceAccessResolver invoiceAccess = mock(InvoiceAccessResolver.class);
        ReceivableAccessResolver receivableAccess = mock(ReceivableAccessResolver.class);
        NonRetryableApplicationException denied = new NonRetryableApplicationException(
                ApplicationErrorCode.ACCESS_DENIED, "test access denial");
        when(contractAccess.access(any(), any(), any())).thenThrow(denied);
        when(invoiceAccess.access(any(), any(), any())).thenThrow(denied);
        when(receivableAccess.access(any(), any(), any())).thenThrow(denied);

        QueryServices services = services(contractAccess, invoiceAccess, receivableAccess);

        assertFailure(services.contract().query(null,
                new ContractQueryRequest().setKeyword(OVER_LIMIT_KEYWORD)), "103");
        assertFailure(services.invoice().query(null,
                new InvoiceQueryRequest().setKeyword(OVER_LIMIT_KEYWORD)), "103");
        assertFailure(services.receivable().query(null,
                new ReceivableQueryRequest().setKeyword(OVER_LIMIT_KEYWORD)), "103");
        assertFailure(services.receivablePlan().query(null,
                new ReceivablePlanQueryRequest().setKeyword(OVER_LIMIT_KEYWORD)), "103");
    }

    @Test
    void overLimitKeywordsAreRejectedAfterAccessHasBeenResolved() {
        ContractAccessResolver contractAccess = mock(ContractAccessResolver.class);
        InvoiceAccessResolver invoiceAccess = mock(InvoiceAccessResolver.class);
        ReceivableAccessResolver receivableAccess = mock(ReceivableAccessResolver.class);
        when(contractAccess.access(any(), any(), any())).thenReturn(new ContractAccess(
                TENANT, MEMBER, new ContractVisibility(
                        DataScope.SELF, MEMBER, Set.of(), ContractAccessMode.READ)));
        when(invoiceAccess.access(any(), any(), any())).thenReturn(new InvoiceAccess(
                TENANT, MEMBER, new InvoiceVisibility(
                        DataScope.SELF, MEMBER, Set.of(), InvoiceAccessMode.READ)));
        when(receivableAccess.access(any(), any(), any())).thenReturn(new ReceivableAccess(
                TENANT, MEMBER, new ReceivableVisibility(
                        DataScope.SELF, MEMBER, Set.of(), ReceivableAccessMode.READ)));

        QueryServices services = services(contractAccess, invoiceAccess, receivableAccess);

        assertFailure(services.contract().query(null,
                new ContractQueryRequest().setKeyword(OVER_LIMIT_KEYWORD)), "101");
        assertFailure(services.invoice().query(null,
                new InvoiceQueryRequest().setKeyword(OVER_LIMIT_KEYWORD)), "101");
        assertFailure(services.receivable().query(null,
                new ReceivableQueryRequest().setKeyword(OVER_LIMIT_KEYWORD)), "101");
        assertFailure(services.receivablePlan().query(null,
                new ReceivablePlanQueryRequest().setKeyword(OVER_LIMIT_KEYWORD)), "101");
        verify(contractAccess).access(null, AuthorityCatalog.CONTRACTS_READ, ContractAccessMode.READ);
        verify(invoiceAccess).access(null, AuthorityCatalog.INVOICES_READ, InvoiceAccessMode.READ);
        verify(receivableAccess).access(null, AuthorityCatalog.RECEIVABLES_READ,
                ReceivableAccessMode.READ);
        verify(receivableAccess).access(null, AuthorityCatalog.RECEIVABLE_PLANS_READ,
                ReceivableAccessMode.READ);
    }

    private static QueryServices services(
            ContractAccessResolver contractAccess, InvoiceAccessResolver invoiceAccess,
            ReceivableAccessResolver receivableAccess) {
        QueryServiceTemplate queries = new QueryServiceTemplate(
                "test-app", new TestTransactionManager());
        return new QueryServices(
                new ContractQueryServiceImpl(null, null, null, null, contractAccess,
                        null, null, null, null, null, null, null, null, null,
                        receivableAccess, new CustomerCsvExportAdmission(), queries),
                new InvoiceQueryServiceImpl(null, null, null, null, null, invoiceAccess,
                        null, null, null, null, queries),
                new ReceivableQueryServiceImpl(null, null, null, null, null, receivableAccess,
                        null, null, null, null, null, queries),
                new ReceivablePlanQueryServiceImpl(null, null, null, null, null, receivableAccess,
                        null, null, null, null, null, null, queries));
    }

    private static void assertFailure(Result<?> result, String errorSpecific) {
        assertFalse(result.isSuccess());
        assertEquals(errorSpecific, result.getErrorContext().fetchRootError()
                .getErrorCode().getErrorSpecific());
    }

    private record QueryServices(
            ContractQueryServiceImpl contract,
            InvoiceQueryServiceImpl invoice,
            ReceivableQueryServiceImpl receivable,
            ReceivablePlanQueryServiceImpl receivablePlan) {
    }
}
