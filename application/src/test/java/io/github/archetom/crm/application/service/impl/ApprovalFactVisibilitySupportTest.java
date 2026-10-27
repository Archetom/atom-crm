package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.domain.model.ApprovalInstanceStatus;
import io.github.archetom.crm.domain.model.ApprovalInstanceSummary;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.policy.ContractFieldCatalog;
import io.github.archetom.crm.domain.policy.InvoiceFieldCatalog;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalFactVisibilitySupportTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final AuthenticatedCaller CALLER =
            new AuthenticatedCaller(7L, TENANT.getValue(), Set.of());

    private BusinessRecordAccessGuard recordAccessGuard;
    private ApprovalFactVisibilitySupport support;

    @BeforeEach
    void setUp() {
        recordAccessGuard = mock(BusinessRecordAccessGuard.class);
        support = new ApprovalFactVisibilitySupport(recordAccessGuard);
    }

    @Test
    void contractAndInvoiceRelationshipFactsUseIndependentBatchedVisibility() {
        ApprovalInstanceSummary contract = summary(1L, BusinessObjectType.CONTRACT, Map.of(
                code(ContractFieldCatalog.CUSTOMER), List.of("11"),
                code(ContractFieldCatalog.OPPORTUNITY), List.of("21"),
                code(ContractFieldCatalog.CONTACT), List.of("31")));
        ApprovalInstanceSummary invoice = summary(2L, BusinessObjectType.INVOICE, Map.of(
                code(InvoiceFieldCatalog.CUSTOMER), List.of("12"),
                code(InvoiceFieldCatalog.CONTRACT), List.of("41")));
        when(recordAccessGuard.visibleSubjectIds(CALLER, TENANT,
                BusinessObjectType.CUSTOMER, Set.of(11L, 12L))).thenReturn(Set.of(12L));
        when(recordAccessGuard.visibleSubjectIds(CALLER, TENANT,
                BusinessObjectType.OPPORTUNITY, Set.of(21L))).thenReturn(Set.of());
        when(recordAccessGuard.visibleSubjectIds(CALLER, TENANT,
                BusinessObjectType.CONTACT, Set.of(31L))).thenReturn(Set.of(31L));
        when(recordAccessGuard.visibleSubjectIds(CALLER, TENANT,
                BusinessObjectType.CONTRACT, Set.of(41L))).thenReturn(Set.of());

        Map<Long, Set<FieldCode>> hidden = support.hiddenForSummaries(
                CALLER, TENANT, List.of(contract, invoice));

        assertEquals(Set.of(code(ContractFieldCatalog.CUSTOMER),
                code(ContractFieldCatalog.OPPORTUNITY)), hidden.get(1L));
        assertTrue(hidden.get(2L).contains(code(InvoiceFieldCatalog.CONTRACT)));
        assertFalse(hidden.get(2L).contains(code(InvoiceFieldCatalog.CUSTOMER)));
        verify(recordAccessGuard).visibleSubjectIds(CALLER, TENANT,
                BusinessObjectType.CUSTOMER, Set.of(11L, 12L));
    }

    @Test
    void missingRelationshipAuthorityFailsClosedAndMalformedIdsStayHidden() {
        ApprovalInstanceSummary contract = summary(1L, BusinessObjectType.CONTRACT, Map.of(
                code(ContractFieldCatalog.CUSTOMER), List.of("11"),
                code(ContractFieldCatalog.OPPORTUNITY), List.of("not-an-id", "21")));
        when(recordAccessGuard.visibleSubjectIds(CALLER, TENANT,
                BusinessObjectType.CUSTOMER, Set.of(11L))).thenThrow(
                new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED));

        Set<FieldCode> hidden = support.hiddenForSummaries(
                CALLER, TENANT, List.of(contract)).get(1L);

        assertTrue(hidden.contains(code(ContractFieldCatalog.CUSTOMER)));
        assertTrue(hidden.contains(code(ContractFieldCatalog.OPPORTUNITY)));
    }

    @Test
    void unexpectedVisibilityFailureIsNotSuppressed() {
        ApprovalInstanceSummary contract = summary(1L, BusinessObjectType.CONTRACT, Map.of(
                code(ContractFieldCatalog.CUSTOMER), List.of("11")));
        when(recordAccessGuard.visibleSubjectIds(CALLER, TENANT,
                BusinessObjectType.CUSTOMER, Set.of(11L))).thenThrow(
                new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID));

        assertThrows(NonRetryableApplicationException.class, () ->
                support.hiddenForSummaries(CALLER, TENANT, List.of(contract)));
    }

    private ApprovalInstanceSummary summary(
            Long id, BusinessObjectType objectType, Map<FieldCode, List<String>> facts) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 10, 0);
        return new ApprovalInstanceSummary(new ApprovalInstanceId(id),
                new ApprovalDefinitionId(3L), 1L, objectType, 100L + id,
                "approval-" + id, "Approval " + id, new MemberId(7L),
                ApprovalInstanceStatus.PENDING, 0, facts, 0L, now, null, now);
    }

    private FieldCode code(String value) {
        return new FieldCode(value);
    }
}
