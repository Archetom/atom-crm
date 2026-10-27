package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.LeadCsvExportRequest;
import io.github.archetom.crm.application.export.LeadCsvDocument;
import io.github.archetom.crm.application.security.CallerGuard;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.LeadAccessResolver;
import io.github.archetom.crm.application.security.SubordinateOwnerResolver;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.LeadAccessMode;
import io.github.archetom.crm.domain.model.LeadExportCell;
import io.github.archetom.crm.domain.model.LeadExportQuery;
import io.github.archetom.crm.domain.model.LeadExportRow;
import io.github.archetom.crm.domain.model.LeadVisibility;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.LeadFieldCatalog;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.LeadRepository;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.LeadId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeadCsvExportServiceImplTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId MEMBER = new MemberId(7L);
    private static final AuthenticatedCaller CALLER = new AuthenticatedCaller(
            MEMBER.getValue(), TENANT.getValue(), Set.of(
            AuthorityCatalog.LEADS_READ, AuthorityCatalog.LEADS_EXPORT));

    @Test
    void exportsOnlyTheAuthorizedProjectionWithFormulaProtection() throws Exception {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.readableFields();
        LeadId leadId = new LeadId(2L);
        when(fixture.leads.findExportLeadIds(eq(TENANT), any(), eq(5_001)))
                .thenReturn(List.of(leadId));
        when(fixture.leads.findExportRows(eq(TENANT), eq(List.of(leadId)), any()))
                .thenReturn(List.of(new LeadExportRow(leadId, List.of(
                        new LeadExportCell(new FieldCode(LeadFieldCatalog.NAME), "\t=SUM(A1:A2)"),
                        new LeadExportCell(new FieldCode(LeadFieldCatalog.LOCKED), "true")))));

        Result<LeadCsvDocument> result = fixture.service().export(CALLER, request(
                LeadFieldCatalog.NAME, LeadFieldCatalog.LOCKED));

        assertTrue(result.isSuccess());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        result.getData().writeTo(output);
        assertEquals("\uFEFFlead_id,lead_name,locked\r\n2,'\t=SUM(A1:A2),true\r\n",
                output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsNonProjectableFieldsBeforeAnyRepositoryProjection() {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.readableFields();

        Result<LeadCsvDocument> result = fixture.service().export(CALLER,
                request(LeadFieldCatalog.CUSTOMER));

        assertFalse(result.isSuccess());
        verify(fixture.leads, never()).findExportLeadIds(any(), any(), anyInt());
        verify(fixture.leads, never()).findExportRows(any(), any(), any());
    }

    @Test
    void intersectsReadAndOwnerScopedExportVisibilityBeforeSelectingRows() {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.readableFields();
        when(fixture.leads.findExportLeadIds(eq(TENANT), any(), eq(5_001)))
                .thenReturn(List.of());

        Result<LeadCsvDocument> result = fixture.service().export(CALLER,
                request(LeadFieldCatalog.NAME));

        assertTrue(result.isSuccess());
        org.mockito.ArgumentCaptor<LeadExportQuery> query =
                org.mockito.ArgumentCaptor.forClass(LeadExportQuery.class);
        verify(fixture.leads).findExportLeadIds(eq(TENANT), query.capture(), eq(5_001));
        assertEquals(LeadAccessMode.READ, query.getValue().readVisibility().accessMode());
        assertEquals(LeadAccessMode.OWNER_SCOPE, query.getValue().exportVisibility().accessMode());
        result.getData().close();
    }

    @Test
    void rejectsFiveThousandAndOneRowsBeforeProjection() {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.readableFields();
        when(fixture.leads.findExportLeadIds(eq(TENANT), any(), eq(5_001)))
                .thenReturn(java.util.stream.LongStream.rangeClosed(1, 5_001)
                        .mapToObj(LeadId::new).toList());

        Result<LeadCsvDocument> result = fixture.service().export(CALLER,
                request(LeadFieldCatalog.NAME));

        assertFalse(result.isSuccess());
        verify(fixture.leads, never()).findExportRows(any(), any(), any());
    }

    private static LeadCsvExportRequest request(String... fieldCodes) {
        return new LeadCsvExportRequest().setFieldCodes(List.of(fieldCodes));
    }

    private static final class Fixture {
        private final LeadRepository leads = mock(LeadRepository.class);
        private final CustomerRepository customers = mock(CustomerRepository.class);
        private final SavedViewRepository views = mock(SavedViewRepository.class);
        private final CallerGuard callerGuard = mock(CallerGuard.class);
        private final LeadAccessResolver leadAccess = mock(LeadAccessResolver.class);
        private final CustomerAccessResolver customerAccess = mock(CustomerAccessResolver.class);
        private final SubordinateOwnerResolver subordinateOwners = mock(SubordinateOwnerResolver.class);
        private final LeadApplicationSupport support = mock(LeadApplicationSupport.class);
        private final CustomerCsvExportAdmission admission = new CustomerCsvExportAdmission();
        private final Map<FieldCode, EffectiveFieldAccess> accesses = new LinkedHashMap<>();
        private final List<FieldDefinition> fields = LeadFieldCatalog.definitions(TENANT);

        private Fixture() {
            when(support.error(any(ApplicationErrorCode.class), anyString())).thenAnswer(invocation ->
                    new NonRetryableApplicationException(
                            invocation.getArgument(0, ApplicationErrorCode.class),
                            invocation.getArgument(1, String.class)));
            when(callerGuard.requireTenant(any(), anyString())).thenReturn(TENANT);
        }

        private void authorize() {
            when(leadAccess.access(CALLER, AuthorityCatalog.LEADS_READ, LeadAccessMode.READ))
                    .thenReturn(new LeadAccessResolver.LeadAccess(TENANT, MEMBER,
                            new LeadVisibility(DataScope.TENANT, MEMBER, Set.of(),
                                    LeadAccessMode.READ)));
            when(leadAccess.access(CALLER, AuthorityCatalog.LEADS_EXPORT,
                    LeadAccessMode.OWNER_SCOPE)).thenReturn(
                    new LeadAccessResolver.LeadAccess(TENANT, MEMBER,
                            new LeadVisibility(DataScope.TENANT, MEMBER, Set.of(),
                                    LeadAccessMode.OWNER_SCOPE)));
        }

        private void readableFields() {
            when(support.fields(TENANT)).thenReturn(fields);
            for (FieldDefinition field : fields) {
                accesses.put(field.getCode(),
                        new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.NONE));
            }
            when(support.accesses(TENANT, MEMBER)).thenReturn(accesses);
        }

        private LeadCsvExportServiceImpl service() {
            return new LeadCsvExportServiceImpl(leads, customers, views, callerGuard,
                    leadAccess, customerAccess, subordinateOwners, support, admission,
                    new QueryServiceTemplate("test", new TestTransactionManager()));
        }
    }
}
