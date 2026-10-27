package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContactCsvExportRequest;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.application.export.ContactCsvDocument;
import io.github.archetom.crm.application.security.CallerGuard;
import io.github.archetom.crm.application.security.ContactAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ContactAccessMode;
import io.github.archetom.crm.domain.model.ContactExportCell;
import io.github.archetom.crm.domain.model.ContactExportQuery;
import io.github.archetom.crm.domain.model.ContactExportRow;
import io.github.archetom.crm.domain.model.ContactVisibility;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ContactFieldCatalog;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContactCsvExportServiceImplTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId MEMBER = new MemberId(7L);
    private static final AuthenticatedCaller CALLER = new AuthenticatedCaller(
            MEMBER.getValue(), TENANT.getValue(), Set.of(
            AuthorityCatalog.CONTACTS_READ, AuthorityCatalog.CONTACTS_EXPORT));

    @Test
    void completesFormulaSafeCsvFromTheContactOnlyProjection() throws Exception {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.readableFields();
        ContactId contactId = new ContactId(2L);
        when(fixture.contacts.findExportContactIds(eq(TENANT), any(), eq(5_001)))
                .thenReturn(List.of(contactId));
        when(fixture.contacts.findExportRows(eq(TENANT), eq(List.of(contactId)), any()))
                .thenReturn(List.of(new ContactExportRow(contactId, List.of(
                        new ContactExportCell(new FieldCode(ContactFieldCatalog.NAME), "\t=SUM(A1:A2)"),
                        new ContactExportCell(new FieldCode(ContactFieldCatalog.NEXT_CONTACT_TIME),
                                "2026-07-24T12:34:56"),
                        new ContactExportCell(new FieldCode(ContactFieldCatalog.LOCKED), "true")))));

        Result<ContactCsvDocument> result = fixture.service().export(CALLER, request(
                ContactFieldCatalog.NAME, ContactFieldCatalog.NEXT_CONTACT_TIME,
                ContactFieldCatalog.LOCKED));

        assertTrue(result.isSuccess());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        result.getData().writeTo(output);
        assertEquals("\uFEFFcontact_id,contact_name,next_contact_time,locked\r\n"
                        + "2,'\t=SUM(A1:A2),2026-07-24T12:34:56,true\r\n",
                output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void encodesEveryContactScalarTypeWithRfc4180AndFormulaBoundaryValues() throws Exception {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.addFields(List.of(
                customField("export_date", CustomFieldType.DATE),
                customField("export_integer", CustomFieldType.INTEGER),
                customField("export_decimal", CustomFieldType.DECIMAL)));
        fixture.readableFields();
        ContactId contactId = new ContactId(2L);
        List<String> fieldCodes = List.of(
                ContactFieldCatalog.NAME, ContactFieldCatalog.DESCRIPTION,
                ContactFieldCatalog.DEAL_STATUS, ContactFieldCatalog.EMAIL,
                ContactFieldCatalog.PHONE_NUMBER, ContactFieldCatalog.WEBSITE,
                ContactFieldCatalog.TELEPHONE, ContactFieldCatalog.NEXT_CONTACT_TIME,
                ContactFieldCatalog.LOCKED, ContactFieldCatalog.CREATED_TIME,
                "export_date", "export_integer", "export_decimal");
        when(fixture.contacts.findExportContactIds(eq(TENANT), any(), eq(5_001)))
                .thenReturn(List.of(contactId));
        when(fixture.contacts.findExportRows(eq(TENANT), eq(List.of(contactId)), any()))
                .thenReturn(List.of(row(contactId, fieldCodes, java.util.Arrays.asList(
                        " \t=SUM(1,1),\"你好\"\r\nnext", "\u0001+sum", "\u200B-minus",
                        "\r@value", "+8613800138000", null, "", "2026-07-24T12:34:56",
                        "true", "2026-07-24T12:34:57", "2026-07-24", "00042", "0003.1400"))));

        Result<ContactCsvDocument> result = fixture.service().export(CALLER,
                new ContactCsvExportRequest().setFieldCodes(fieldCodes));

        assertTrue(result.isSuccess());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        result.getData().writeTo(output);
        assertEquals("\uFEFFcontact_id,contact_name,description,deal_status,email,phone_number,website,telephone,"
                        + "next_contact_time,locked,created_time,export_date,export_integer,export_decimal\r\n"
                        + "2,\"' \t=SUM(1,1),\"\"你好\"\"\r\nnext\",'\u0001+sum,'\u200B-minus,\"'\r@value\","
                        + "'+8613800138000,,,2026-07-24T12:34:56,true,2026-07-24T12:34:57,2026-07-24,42,3.1400\r\n",
                output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void invalidOrMaskedFieldsRejectTheWholeExportBeforeProjection() {
        Fixture masked = new Fixture();
        masked.authorize();
        masked.readableFields();
        masked.accesses.put(new FieldCode(ContactFieldCatalog.NAME),
                new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.FULL));
        assertRejectedBeforeProjection(masked, request(ContactFieldCatalog.NAME));

        Fixture tags = new Fixture();
        tags.authorize();
        tags.readableFields();
        assertRejectedBeforeProjection(tags, request(ContactFieldCatalog.TAGS));

        Fixture unknown = new Fixture();
        unknown.authorize();
        unknown.readableFields();
        assertRejectedBeforeProjection(unknown, request("unknown_csv_field"));

        Fixture duplicate = new Fixture();
        duplicate.authorize();
        duplicate.readableFields();
        assertRejectedBeforeProjection(duplicate,
                request(ContactFieldCatalog.NAME, ContactFieldCatalog.NAME));
    }

    @Test
    void rowProbeRejectsFiveThousandAndOneRowsWithoutStartingProjection() {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.readableFields();
        when(fixture.contacts.findExportContactIds(eq(TENANT), any(), eq(5_001)))
                .thenReturn(java.util.stream.LongStream.rangeClosed(1, 5_001)
                        .mapToObj(ContactId::new).toList());

        Result<ContactCsvDocument> result = fixture.service().export(CALLER,
                request(ContactFieldCatalog.NAME));

        assertFalse(result.isSuccess());
        verify(fixture.contacts, never()).findExportRows(any(), any(), any());
    }

    @Test
    void rejectsADocumentOneByteOverTheSixteenMiBLimitBeforeReturningIt() {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.readableFields();
        ContactId contactId = new ContactId(1L);
        int fixedBytes = "\uFEFFcontact_id,contact_name\r\n1,\r\n".getBytes(StandardCharsets.UTF_8).length;
        String oneByteOverLimit = "a".repeat(ContactCsvExportServiceImpl.MAX_BYTES + 1 - fixedBytes);
        when(fixture.contacts.findExportContactIds(eq(TENANT), any(), eq(5_001)))
                .thenReturn(List.of(contactId));
        when(fixture.contacts.findExportRows(eq(TENANT), eq(List.of(contactId)), any()))
                .thenReturn(List.of(new ContactExportRow(contactId, List.of(
                        new ContactExportCell(new FieldCode(ContactFieldCatalog.NAME), oneByteOverLimit)))));

        Result<ContactCsvDocument> result = fixture.service().export(CALLER,
                request(ContactFieldCatalog.NAME));

        assertFalse(result.isSuccess());
    }

    @Test
    void intersectsOrdinaryReadAndOwnerDerivedExportVisibilityInOneQuery() {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.readableFields();
        when(fixture.contacts.findExportContactIds(eq(TENANT), any(), eq(5_001)))
                .thenReturn(List.of());

        Result<ContactCsvDocument> result = fixture.service().export(CALLER,
                request(ContactFieldCatalog.NAME));

        assertTrue(result.isSuccess());
        org.mockito.ArgumentCaptor<ContactExportQuery> query =
                org.mockito.ArgumentCaptor.forClass(ContactExportQuery.class);
        verify(fixture.contacts).findExportContactIds(eq(TENANT), query.capture(), eq(5_001));
        assertEquals(ContactAccessMode.READ, query.getValue().readVisibility().accessMode());
        assertEquals(ContactAccessMode.OWNER_SCOPE, query.getValue().exportVisibility().accessMode());
        result.getData().close();
    }

    @Test
    void normalizesTemporaryConditionsAndRejectsTheirCombinationWithASavedView() {
        Fixture temporary = new Fixture();
        temporary.authorize();
        temporary.addFields(List.of(customField("segment", CustomFieldType.TEXT)));
        temporary.readableFields();
        when(temporary.contacts.findExportContactIds(eq(TENANT), any(), eq(5_001)))
                .thenReturn(List.of());

        Result<ContactCsvDocument> result = temporary.service().export(CALLER,
                request(ContactFieldCatalog.NAME).setConditions(List.of(new FilterConditionRequest()
                        .setFieldCode("segment").setOperator("EQ")
                        .setValues(List.of(" enterprise ")))));

        assertTrue(result.isSuccess());
        org.mockito.ArgumentCaptor<ContactExportQuery> query =
                org.mockito.ArgumentCaptor.forClass(ContactExportQuery.class);
        verify(temporary.contacts).findExportContactIds(eq(TENANT), query.capture(), eq(5_001));
        assertEquals(List.of("enterprise"), query.getValue().conditions().getFirst().values());
        result.getData().close();

        Fixture conflicting = new Fixture();
        Result<ContactCsvDocument> conflict = conflicting.service().export(CALLER,
                request(ContactFieldCatalog.NAME).setSavedViewId(1L)
                        .setConditions(List.of(new FilterConditionRequest().setFieldCode("segment")
                                .setOperator("EQ").setValues(List.of("enterprise")))));

        assertFalse(conflict.isSuccess());
        verify(conflicting.contacts, never()).findExportContactIds(any(), any(), anyInt());
    }

    @Test
    void contactExportSharesTheCustomerAdmissionBudgetPerMember() {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.readableFields();
        CustomerCsvExportAdmission.Lease customerLease = fixture.admission.tryAcquire(TENANT, MEMBER);

        Result<ContactCsvDocument> result = fixture.service().export(CALLER,
                request(ContactFieldCatalog.NAME));

        assertFalse(result.isSuccess());
        assertNull(fixture.admission.tryAcquire(TENANT, MEMBER));
        verify(fixture.contacts, never()).findExportContactIds(any(), any(), anyInt());
        customerLease.close();
    }

    @Test
    void contactExportCannotExceedTheFourSlotCustomerContactReplicaBudget() {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.readableFields();
        List<CustomerCsvExportAdmission.Lease> customerLeases = new ArrayList<>();
        try {
            for (long memberId = 20L; memberId < 24L; memberId++) {
                CustomerCsvExportAdmission.Lease lease = fixture.admission.tryAcquire(
                        TENANT, new MemberId(memberId));
                assertTrue(lease != null);
                customerLeases.add(lease);
            }

            Result<ContactCsvDocument> result = fixture.service().export(CALLER,
                    request(ContactFieldCatalog.NAME));

            assertFalse(result.isSuccess());
            verify(fixture.contacts, never()).findExportContactIds(any(), any(), anyInt());
        } finally {
            customerLeases.forEach(CustomerCsvExportAdmission.Lease::close);
        }
    }

    private static void assertRejectedBeforeProjection(
            Fixture fixture, ContactCsvExportRequest request) {
        assertFalse(fixture.service().export(CALLER, request).isSuccess());
        verify(fixture.contacts, never()).findExportContactIds(any(), any(), anyInt());
        verify(fixture.contacts, never()).findExportRows(any(), any(), any());
    }

    private static ContactCsvExportRequest request(String... fieldCodes) {
        return new ContactCsvExportRequest().setFieldCodes(List.of(fieldCodes));
    }

    private static FieldDefinition customField(String code, CustomFieldType type) {
        return FieldDefinition.createCustom(TENANT, BusinessObjectType.CONTACT, new FieldCode(code),
                code, null, type, false, false, true, 200, "Export", List.of());
    }

    private static ContactExportRow row(
            ContactId contactId, List<String> fieldCodes, List<String> values) {
        List<ContactExportCell> cells = new ArrayList<>();
        for (int index = 0; index < fieldCodes.size(); index++) {
            cells.add(new ContactExportCell(new FieldCode(fieldCodes.get(index)), values.get(index)));
        }
        return new ContactExportRow(contactId, cells);
    }

    private static final class Fixture {
        private final ContactRepository contacts = mock(ContactRepository.class);
        private final CustomerRepository customers = mock(CustomerRepository.class);
        private final SavedViewRepository views = mock(SavedViewRepository.class);
        private final CallerGuard callerGuard = mock(CallerGuard.class);
        private final ContactAccessResolver contactAccess = mock(ContactAccessResolver.class);
        private final CustomerAccessResolver customerAccess = mock(CustomerAccessResolver.class);
        private final ContactApplicationSupport support = mock(ContactApplicationSupport.class);
        private final CustomerCsvExportAdmission admission = new CustomerCsvExportAdmission();
        private final Map<FieldCode, EffectiveFieldAccess> accesses = new LinkedHashMap<>();
        private final List<FieldDefinition> fields = new ArrayList<>(ContactFieldCatalog.definitions(TENANT));

        private Fixture() {
            when(support.error(any(ApplicationErrorCode.class), anyString())).thenAnswer(invocation ->
                    new NonRetryableApplicationException(
                            invocation.getArgument(0, ApplicationErrorCode.class),
                            invocation.getArgument(1, String.class)));
            when(callerGuard.requireTenant(any(), anyString())).thenReturn(TENANT);
        }

        private void authorize() {
            when(contactAccess.access(CALLER, AuthorityCatalog.CONTACTS_READ, ContactAccessMode.READ))
                    .thenReturn(new ContactAccessResolver.ContactAccess(TENANT, MEMBER,
                            new ContactVisibility(DataScope.TENANT, MEMBER, Set.of(),
                                    ContactAccessMode.READ)));
            when(contactAccess.access(CALLER, AuthorityCatalog.CONTACTS_EXPORT,
                    ContactAccessMode.OWNER_SCOPE)).thenReturn(
                    new ContactAccessResolver.ContactAccess(TENANT, MEMBER,
                            new ContactVisibility(DataScope.TENANT, MEMBER, Set.of(),
                                    ContactAccessMode.OWNER_SCOPE)));
        }

        private void readableFields() {
            when(support.fields(TENANT)).thenReturn(fields);
            for (FieldDefinition field : fields) {
                accesses.put(field.getCode(),
                        new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.NONE));
            }
            when(support.accesses(TENANT, MEMBER)).thenReturn(accesses);
        }

        private void addFields(List<FieldDefinition> additional) {
            fields.addAll(additional);
        }

        private ContactCsvExportServiceImpl service() {
            return new ContactCsvExportServiceImpl(contacts, customers, views, callerGuard,
                    contactAccess, customerAccess, support, admission,
                    new QueryServiceTemplate("test", new TestTransactionManager()));
        }
    }
}
