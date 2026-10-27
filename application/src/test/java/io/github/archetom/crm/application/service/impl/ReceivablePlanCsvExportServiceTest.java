package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReceivablePlanCsvExportRequest;
import io.github.archetom.crm.application.export.ReceivablePlanCsvDocument;
import io.github.archetom.crm.application.security.ContractAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.ReceivableAccessResolver;
import io.github.archetom.crm.application.security.SubordinateOwnerResolver;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.application.vo.ReceivablePlanVO;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.ReceivablePlan;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.ReceivableAccessMode;
import io.github.archetom.crm.domain.model.ReceivablePlanQuery;
import io.github.archetom.crm.domain.model.ReceivableVisibility;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ReceivablePlanFieldCatalog;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.ReceivablePlanRepository;
import io.github.archetom.crm.domain.repository.ReceivableRepository;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceivablePlanCsvExportServiceTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId MEMBER = new MemberId(7L);
    private static final AuthenticatedCaller CALLER = new AuthenticatedCaller(
            MEMBER.getValue(), TENANT.getValue(), Set.of(
            AuthorityCatalog.RECEIVABLE_PLANS_READ, AuthorityCatalog.RECEIVABLE_PLANS_EXPORT));

    @Test
    void exportsOnlyTheAuthorizedProjectionWithFormulaProtection() throws Exception {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.readableFields();
        when(fixture.plans.findExportPlans(eq(TENANT), any(), any(), eq(5_001)))
                .thenReturn(List.of(mock(ReceivablePlan.class)));
        when(fixture.output.plans(eq(CALLER), anyList(), any(), any())).thenReturn(List.of(
                new ReceivablePlanVO(2L, 7L, null, null, 1, LocalDate.now(), null,
                        "\t=SUM(A1:A2)", null, null, null, null, null, null, Map.of(),
                        0L, LocalDateTime.now(), LocalDateTime.now())));

        Result<ReceivablePlanCsvDocument> result = fixture.service().export(CALLER,
                request(ReceivablePlanFieldCatalog.PAYMENT_METHOD));

        assertTrue(result.isSuccess());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        result.getData().writeTo(output);
        assertEquals("\uFEFFreceivable_plan_id,payment_method\r\n2,'\t=SUM(A1:A2)\r\n",
                output.toString(StandardCharsets.UTF_8));
        verify(fixture.support).requireReadableUnmasked(any(),
                eq(ReceivablePlanFieldCatalog.PAYMENT_METHOD));
    }

    @Test
    void appliesAnIndependentOwnerScopedExportPredicate() {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.readableFields();
        when(fixture.plans.findExportPlans(eq(TENANT), any(), any(), eq(5_001)))
                .thenReturn(List.of());

        Result<ReceivablePlanCsvDocument> result = fixture.service().export(CALLER,
                request(ReceivablePlanFieldCatalog.PAYMENT_METHOD));

        assertTrue(result.isSuccess());
        org.mockito.ArgumentCaptor<ReceivablePlanQuery> query =
                org.mockito.ArgumentCaptor.forClass(ReceivablePlanQuery.class);
        org.mockito.ArgumentCaptor<ReceivableVisibility> exportVisibility =
                org.mockito.ArgumentCaptor.forClass(ReceivableVisibility.class);
        verify(fixture.plans).findExportPlans(eq(TENANT), query.capture(),
                exportVisibility.capture(), eq(5_001));
        assertEquals(ReceivableAccessMode.READ, query.getValue().visibility().accessMode());
        assertEquals(ReceivableAccessMode.OWNER_SCOPE,
                exportVisibility.getValue().accessMode());
        result.getData().close();
    }

    @Test
    void rejectsMaskedFieldsBeforeFetchingRows() {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.readableFields();
        doThrow(new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED,
                "Receivable field is not readable")).when(fixture.support)
                .requireReadableUnmasked(any(), eq(ReceivablePlanFieldCatalog.REMARK));

        Result<ReceivablePlanCsvDocument> result = fixture.service().export(CALLER,
                request(ReceivablePlanFieldCatalog.REMARK));

        assertFalse(result.isSuccess());
        verify(fixture.plans, never()).findExportPlans(any(), any(), any(), anyInt());
    }

    private static ReceivablePlanCsvExportRequest request(String... fieldCodes) {
        return new ReceivablePlanCsvExportRequest().setFieldCodes(List.of(fieldCodes));
    }

    private static final class Fixture {
        private final ReceivablePlanRepository plans = mock(ReceivablePlanRepository.class);
        private final CustomerRepository customers = mock(CustomerRepository.class);
        private final ContractRepository contracts = mock(ContractRepository.class);
        private final ReceivableRepository receivables = mock(ReceivableRepository.class);
        private final SavedViewRepository views = mock(SavedViewRepository.class);
        private final ReceivableAccessResolver access = mock(ReceivableAccessResolver.class);
        private final CustomerAccessResolver customerAccess = mock(CustomerAccessResolver.class);
        private final ContractAccessResolver contractAccess = mock(ContractAccessResolver.class);
        private final SubordinateOwnerResolver subordinateOwners = mock(SubordinateOwnerResolver.class);
        private final ReceivableApplicationSupport support = mock(ReceivableApplicationSupport.class);
        private final ReceivableOutputSupport output = mock(ReceivableOutputSupport.class);
        private final CustomerCsvExportAdmission admission = new CustomerCsvExportAdmission();
        private final Map<FieldCode, EffectiveFieldAccess> accesses = new LinkedHashMap<>();
        private final List<FieldDefinition> fields = ReceivablePlanFieldCatalog.definitions(TENANT);

        private Fixture() {
            when(support.error(any(ApplicationErrorCode.class), anyString())).thenAnswer(invocation ->
                    new NonRetryableApplicationException(
                            invocation.getArgument(0, ApplicationErrorCode.class),
                            invocation.getArgument(1, String.class)));
        }

        private void authorize() {
            when(access.access(CALLER, AuthorityCatalog.RECEIVABLE_PLANS_READ,
                    ReceivableAccessMode.READ)).thenReturn(new ReceivableAccessResolver.ReceivableAccess(
                    TENANT, MEMBER, new ReceivableVisibility(DataScope.TENANT, MEMBER, Set.of(),
                    ReceivableAccessMode.READ)));
            when(access.access(CALLER, AuthorityCatalog.RECEIVABLE_PLANS_EXPORT,
                    ReceivableAccessMode.OWNER_SCOPE)).thenReturn(
                    new ReceivableAccessResolver.ReceivableAccess(TENANT, MEMBER,
                            new ReceivableVisibility(DataScope.TENANT, MEMBER, Set.of(),
                                    ReceivableAccessMode.OWNER_SCOPE)));
        }

        private void readableFields() {
            when(support.fields(TENANT, BusinessObjectType.RECEIVABLE_PLAN)).thenReturn(fields);
            for (FieldDefinition field : fields) {
                accesses.put(field.getCode(),
                        new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.NONE));
            }
            when(support.accesses(TENANT, MEMBER, BusinessObjectType.RECEIVABLE_PLAN))
                    .thenReturn(accesses);
        }

        private ReceivablePlanQueryServiceImpl service() {
            return new ReceivablePlanQueryServiceImpl(plans, customers, contracts, receivables, views,
                    access, customerAccess, contractAccess, subordinateOwners, support, output, admission,
                    new QueryServiceTemplate("test", new TestTransactionManager()));
        }
    }
}
