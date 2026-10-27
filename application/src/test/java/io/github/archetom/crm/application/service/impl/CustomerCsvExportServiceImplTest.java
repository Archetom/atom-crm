package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.CustomerCsvExportRequest;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.application.export.CustomerCsvDocument;
import io.github.archetom.crm.application.security.CallerGuard;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.SubordinateOwnerResolver;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.CustomerExportCell;
import io.github.archetom.crm.domain.model.CustomerExportRow;
import io.github.archetom.crm.domain.model.CustomerVisibility;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerCsvExportServiceImplTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId MEMBER = new MemberId(7L);
    private static final DepartmentId DEPARTMENT = new DepartmentId(3L);
    private static final AuthenticatedCaller CALLER = new AuthenticatedCaller(
            MEMBER.getValue(), TENANT.getValue(), Set.of(
            AuthorityCatalog.CUSTOMERS_READ, AuthorityCatalog.CUSTOMERS_EXPORT));

    @Test
    void completesFormulaSafeCsvBeforeReturningTheDocument() throws Exception {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.readableFields();
        CustomerId customerId = new CustomerId(2L);
        when(fixture.customers.findExportCustomerIds(eq(TENANT), any(), eq(5_001)))
                .thenReturn(List.of(customerId));
        when(fixture.customers.findExportRows(eq(TENANT), eq(List.of(customerId)), any()))
                .thenReturn(List.of(new CustomerExportRow(customerId, List.of(
                        new CustomerExportCell(new FieldCode(CustomerFieldCatalog.NAME), "\t=SUM(A1:A2)"),
                        new CustomerExportCell(new FieldCode(CustomerFieldCatalog.LOCKED), "true"),
                        new CustomerExportCell(new FieldCode(CustomerFieldCatalog.CREATED_TIME),
                                "2026-07-24T12:34:56")))));

        var result = fixture.service().export(CALLER, request(
                CustomerFieldCatalog.NAME, CustomerFieldCatalog.LOCKED,
                CustomerFieldCatalog.CREATED_TIME));

        assertTrue(result.isSuccess());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        result.getData().writeTo(output);
        assertEquals("\uFEFFcustomer_id,customer_name,locked,created_time\r\n"
                        + "2,'\t=SUM(A1:A2),true,2026-07-24T12:34:56\r\n",
                output.toString(StandardCharsets.UTF_8));
        verify(fixture.customers).findExportCustomerIds(eq(TENANT), any(), eq(5_001));
    }

    @Test
    void appliesOnlyServerResolvedSubordinateOwnersToCsvExport() {
        Fixture fixture = new Fixture();
        fixture.authorize(DataScope.DEPARTMENT_SUBTREE);
        fixture.readableFields();
        MemberId subordinate = new MemberId(8L);
        when(fixture.subordinates.resolve(TENANT, MEMBER, DataScope.DEPARTMENT_SUBTREE))
                .thenReturn(Set.of(subordinate));
        when(fixture.customers.findExportCustomerIds(eq(TENANT), any(), eq(5_001)))
                .thenReturn(List.of());

        CustomerCsvExportRequest request = request(CustomerFieldCatalog.NAME)
                .setSubordinateOwnerOnly(true);
        var result = fixture.service().export(CALLER, request);

        assertTrue(result.isSuccess());
        ArgumentCaptor<io.github.archetom.crm.domain.model.CustomerExportQuery> query =
                ArgumentCaptor.forClass(io.github.archetom.crm.domain.model.CustomerExportQuery.class);
        verify(fixture.customers).findExportCustomerIds(eq(TENANT), query.capture(), eq(5_001));
        assertEquals(Set.of(subordinate), query.getValue().subordinateOwnerMemberIds());
    }

    @Test
    void rejectsCombinedExplicitAndSubordinateCustomerExportScenes() {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.readableFields();

        CustomerCsvExportRequest request = request(CustomerFieldCatalog.NAME)
                .setOwnerActorId(8L)
                .setSubordinateOwnerOnly(true);

        assertRejectedBeforeProjection(fixture, request);
    }

    @Test
    void appliesValidatedTemporaryConditionsToCsvExport() {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.readableFields();
        when(fixture.customers.findExportCustomerIds(eq(TENANT), any(), eq(5_001)))
                .thenReturn(List.of());

        CustomerCsvExportRequest request = request(CustomerFieldCatalog.NAME)
                .setConditions(List.of(new FilterConditionRequest()
                        .setFieldCode(CustomerFieldCatalog.NAME)
                        .setOperator("CONTAINS")
                        .setValues(List.of("  Acme  "))));

        var result = fixture.service().export(CALLER, request);

        assertTrue(result.isSuccess());
        ArgumentCaptor<io.github.archetom.crm.domain.model.CustomerExportQuery> query =
                ArgumentCaptor.forClass(io.github.archetom.crm.domain.model.CustomerExportQuery.class);
        verify(fixture.customers).findExportCustomerIds(eq(TENANT), query.capture(), eq(5_001));
        assertEquals(List.of("Acme"), query.getValue().conditions().getFirst().values());
    }

    @Test
    void rejectsSavedAndTemporaryCustomerExportFiltersTogether() {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.readableFields();

        CustomerCsvExportRequest request = request(CustomerFieldCatalog.NAME)
                .setSavedViewId(1L)
                .setConditions(List.of(new FilterConditionRequest()
                        .setFieldCode(CustomerFieldCatalog.NAME)
                        .setOperator("IS_NOT_EMPTY")
                        .setValues(List.of())));

        assertRejectedBeforeProjection(fixture, request);
    }

    @Test
    void maskedFieldRejectsTheWholeExportBeforeAnyCustomerProjection() {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.readableFields();
        fixture.accesses.put(new FieldCode(CustomerFieldCatalog.NAME),
                new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.FULL));

        var result = fixture.service().export(CALLER, request(CustomerFieldCatalog.NAME));

        assertFalse(result.isSuccess());
        verify(fixture.customers, never()).findExportCustomerIds(any(), any(), anyInt());
        verify(fixture.customers, never()).findExportRows(any(), any(), any());
    }

    @Test
    void collectionFieldIsRejectedBeforeAnyCustomerProjection() {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.readableFields();

        var result = fixture.service().export(CALLER, request(CustomerFieldCatalog.TAGS));

        assertFalse(result.isSuccess());
        verify(fixture.customers, never()).findExportCustomerIds(any(), any(), anyInt());
        verify(fixture.customers, never()).findExportRows(any(), any(), any());
    }

    @Test
    void hiddenInactiveUnknownDuplicateAndUnsupportedFieldsRejectTheWholeExport() {
        Fixture hidden = new Fixture();
        hidden.authorize();
        hidden.readableFields();
        hidden.accesses.put(new FieldCode(CustomerFieldCatalog.NAME),
                new EffectiveFieldAccess(FieldAccessLevel.HIDDEN, FieldMaskType.NONE));
        assertRejectedBeforeProjection(hidden, request(CustomerFieldCatalog.NAME));

        Fixture unknown = new Fixture();
        unknown.authorize();
        unknown.readableFields();
        assertRejectedBeforeProjection(unknown, request("unknown_csv_field"));

        Fixture duplicate = new Fixture();
        duplicate.authorize();
        duplicate.readableFields();
        assertRejectedBeforeProjection(duplicate,
                request(CustomerFieldCatalog.NAME, CustomerFieldCatalog.NAME));

        Fixture inactive = new Fixture();
        FieldDefinition inactiveField = customField("inactive_csv_field", CustomFieldType.TEXT);
        inactiveField.deactivate();
        inactive.addFields(List.of(inactiveField));
        inactive.authorize();
        inactive.readableFields();
        assertRejectedBeforeProjection(inactive, request("inactive_csv_field"));

        for (CustomFieldType unsupported : List.of(
                CustomFieldType.FILE, CustomFieldType.USER, CustomFieldType.RELATION,
                CustomFieldType.ADDRESS)) {
            Fixture fixture = new Fixture();
            String code = "unsupported_" + unsupported.name().toLowerCase();
            fixture.addFields(List.of(nonSearchableCustomField(code, unsupported)));
            fixture.authorize();
            fixture.readableFields();
            assertRejectedBeforeProjection(fixture, request(code));
        }
    }

    @Test
    void rowProbeRejectsFiveThousandAndOneRowsWithoutTruncating() {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.readableFields();
        when(fixture.customers.findExportCustomerIds(eq(TENANT), any(), eq(5_001)))
                .thenReturn(java.util.stream.LongStream.rangeClosed(1, 5_001)
                        .mapToObj(CustomerId::new).toList());

        var result = fixture.service().export(CALLER, request(CustomerFieldCatalog.NAME));

        assertFalse(result.isSuccess());
        verify(fixture.customers, never()).findExportRows(any(), any(), any());
    }

    @Test
    void encodesEveryScalarTypeWithRfc4180AndFormulaBoundaryValues() throws Exception {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.addFields(List.of(
                customField("export_date", CustomFieldType.DATE),
                customField("export_integer", CustomFieldType.INTEGER),
                customField("export_decimal", CustomFieldType.DECIMAL)));
        fixture.readableFields();
        CustomerId customerId = new CustomerId(2L);
        List<String> fieldCodes = List.of(
                CustomerFieldCatalog.NAME, CustomerFieldCatalog.DESCRIPTION,
                CustomerFieldCatalog.DEAL_STATUS, CustomerFieldCatalog.EMAIL,
                CustomerFieldCatalog.PHONE_NUMBER, CustomerFieldCatalog.WEBSITE,
                CustomerFieldCatalog.LOCKED, CustomerFieldCatalog.CREATED_TIME,
                "export_date", "export_integer", "export_decimal");
        when(fixture.customers.findExportCustomerIds(eq(TENANT), any(), eq(5_001)))
                .thenReturn(List.of(customerId));
        when(fixture.customers.findExportRows(eq(TENANT), eq(List.of(customerId)), any()))
                .thenReturn(List.of(row(customerId, fieldCodes, java.util.Arrays.asList(
                        " \t=SUM(1,1),\"你好\"\r\nnext", "\u0001+sum", "\u200B-minus",
                        "\r@value", null, "", "true", "2026-07-24T12:34:56",
                        "2026-07-24", "00042", "0003.1400"))));

        var result = fixture.service().export(CALLER,
                new CustomerCsvExportRequest().setFieldCodes(fieldCodes));

        assertTrue(result.isSuccess());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        result.getData().writeTo(output);
        assertEquals("\uFEFFcustomer_id,customer_name,description,deal_status,email,phone_number,website,locked,"
                        + "created_time,export_date,export_integer,export_decimal\r\n"
                        + "2,\"' \t=SUM(1,1),\"\"你好\"\"\r\nnext\",'\u0001+sum,'\u200B-minus,\"'\r@value\",,,true,"
                        + "2026-07-24T12:34:56,2026-07-24,42,3.1400\r\n",
                output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void fetchesTwoHundredCustomerIdsPerProjectionBatch() {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.readableFields();
        List<CustomerId> ids = LongStream.rangeClosed(1, 201).mapToObj(CustomerId::new).toList();
        when(fixture.customers.findExportCustomerIds(eq(TENANT), any(), eq(5_001))).thenReturn(ids);
        when(fixture.customers.findExportRows(eq(TENANT), anyList(), any())).thenAnswer(invocation ->
                ((List<CustomerId>) invocation.getArgument(1)).stream()
                        .map(id -> row(id, List.of(CustomerFieldCatalog.NAME), List.of("Customer " + id.getValue())))
                        .toList());

        var result = fixture.service().export(CALLER, request(CustomerFieldCatalog.NAME));

        assertTrue(result.isSuccess());
        result.getData().close();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CustomerId>> batches = ArgumentCaptor.forClass((Class) List.class);
        verify(fixture.customers, times(2)).findExportRows(eq(TENANT), batches.capture(), any());
        assertEquals(List.of(200, 1), batches.getAllValues().stream().map(List::size).toList());
        assertEquals(ids, batches.getAllValues().stream().flatMap(List::stream).toList());
    }

    @Test
    void rejectsADocumentOneByteOverTheSixteenMiBLimitBeforeReturningIt() {
        Fixture fixture = new Fixture();
        fixture.authorize();
        fixture.readableFields();
        CustomerId customerId = new CustomerId(1L);
        int fixedBytes = "\uFEFFcustomer_id,customer_name\r\n1,\r\n".getBytes(StandardCharsets.UTF_8).length;
        String oneByteOverLimit = "a".repeat(CustomerCsvExportServiceImpl.MAX_BYTES + 1 - fixedBytes);
        when(fixture.customers.findExportCustomerIds(eq(TENANT), any(), eq(5_001)))
                .thenReturn(List.of(customerId));
        when(fixture.customers.findExportRows(eq(TENANT), eq(List.of(customerId)), any()))
                .thenReturn(List.of(row(customerId, List.of(CustomerFieldCatalog.NAME),
                        List.of(oneByteOverLimit))));

        var result = fixture.service().export(CALLER, request(CustomerFieldCatalog.NAME));

        assertFalse(result.isSuccess());
    }

    @Test
    void admissionHoldsOneMemberUntilTheDocumentStreamClosesAndCapsTheReplicaAtFour() throws Exception {
        CustomerCsvExportAdmission admission = new CustomerCsvExportAdmission();
        CustomerCsvExportAdmission.Lease first = admission.tryAcquire(TENANT, MEMBER);
        assertNotNull(first);
        CustomerCsvDocument document = new CustomerCsvDocument(new byte[] {1}, first);

        assertNull(admission.tryAcquire(TENANT, MEMBER));
        CustomerCsvExportAdmission.Lease second = admission.tryAcquire(TENANT, new MemberId(8L));
        CustomerCsvExportAdmission.Lease third = admission.tryAcquire(TENANT, new MemberId(9L));
        CustomerCsvExportAdmission.Lease fourth = admission.tryAcquire(TENANT, new MemberId(10L));
        assertNotNull(second);
        assertNotNull(third);
        assertNotNull(fourth);
        assertNull(admission.tryAcquire(TENANT, new MemberId(11L)));

        try (var ignored = document.openStream()) {
            // Closing the HTTP resource's stream must release the document lease.
        }
        CustomerCsvExportAdmission.Lease reacquired = admission.tryAcquire(TENANT, MEMBER);
        assertNotNull(reacquired);
        reacquired.close();
        second.close();
        third.close();
        fourth.close();
    }

    @Test
    void fifthAdmittedRequestDoesNotOpenAQueryTransactionOrAccessRepositories() {
        CountingTransactionManager transactions = new CountingTransactionManager();
        Fixture fixture = new Fixture(transactions);
        fixture.readableFields();
        when(fixture.customers.findExportCustomerIds(eq(TENANT), any(), eq(5_001)))
                .thenReturn(List.of());
        List<CustomerCsvDocument> documents = new ArrayList<>();
        for (long actorId = 20L; actorId < 24L; actorId++) {
            AuthenticatedCaller caller = caller(actorId);
            fixture.authorize(caller, new MemberId(actorId));
            var result = fixture.service().export(caller, request(CustomerFieldCatalog.NAME));
            assertTrue(result.isSuccess());
            documents.add(result.getData());
        }

        AuthenticatedCaller fifthCaller = caller(24L);
        var rejected = fixture.service().export(fifthCaller, request(CustomerFieldCatalog.NAME));

        assertFalse(rejected.isSuccess());
        assertEquals(4, transactions.transactionCount());
        verify(fixture.access, times(8)).access(any(), anyString(), any());
        verify(fixture.customers, times(4)).findExportCustomerIds(eq(TENANT), any(), eq(5_001));
        verify(fixture.customers, never()).findExportRows(any(), any(), any());
        documents.forEach(CustomerCsvDocument::close);
    }

    private static void assertRejectedBeforeProjection(
            Fixture fixture, CustomerCsvExportRequest request) {
        assertFalse(fixture.service().export(CALLER, request).isSuccess());
        verify(fixture.customers, never()).findExportCustomerIds(any(), any(), anyInt());
        verify(fixture.customers, never()).findExportRows(any(), any(), any());
    }

    private static AuthenticatedCaller caller(long actorId) {
        return new AuthenticatedCaller(actorId, TENANT.getValue(), Set.of(
                AuthorityCatalog.CUSTOMERS_READ, AuthorityCatalog.CUSTOMERS_EXPORT));
    }

    private static FieldDefinition customField(String code, CustomFieldType type) {
        return FieldDefinition.createCustom(TENANT, BusinessObjectType.CUSTOMER, new FieldCode(code),
                code, null, type, false, false, true, 200, "Export", List.of());
    }

    private static FieldDefinition nonSearchableCustomField(String code, CustomFieldType type) {
        return FieldDefinition.createCustom(TENANT, BusinessObjectType.CUSTOMER, new FieldCode(code),
                code, null, type, false, false, false, 200, "Export", List.of());
    }

    private static CustomerExportRow row(
            CustomerId customerId, List<String> fieldCodes, List<String> values) {
        List<CustomerExportCell> cells = new ArrayList<>();
        for (int index = 0; index < fieldCodes.size(); index++) {
            cells.add(new CustomerExportCell(new FieldCode(fieldCodes.get(index)), values.get(index)));
        }
        return new CustomerExportRow(customerId, cells);
    }

    private static CustomerCsvExportRequest request(String... fieldCodes) {
        return new CustomerCsvExportRequest().setFieldCodes(List.of(fieldCodes));
    }

    private static final class Fixture {
        private final CustomerRepository customers = mock(CustomerRepository.class);
        private final SavedViewRepository views = mock(SavedViewRepository.class);
        private final CallerGuard callerGuard = mock(CallerGuard.class);
        private final CustomerAccessResolver access = mock(CustomerAccessResolver.class);
        private final SubordinateOwnerResolver subordinates = mock(SubordinateOwnerResolver.class);
        private final CustomerApplicationSupport support = mock(CustomerApplicationSupport.class);
        private final CustomerCsvExportAdmission admission = new CustomerCsvExportAdmission();
        private final Map<FieldCode, EffectiveFieldAccess> accesses = new LinkedHashMap<>();
        private final List<FieldDefinition> fields = new ArrayList<>(CustomerFieldCatalog.definitions(TENANT));
        private final PlatformTransactionManager transactionManager;

        private Fixture() {
            this(new TestTransactionManager());
        }

        private Fixture(PlatformTransactionManager transactionManager) {
            this.transactionManager = transactionManager;
            when(support.error(any(ApplicationErrorCode.class), anyString())).thenAnswer(invocation ->
                    new NonRetryableApplicationException(
                            invocation.getArgument(0, ApplicationErrorCode.class),
                            invocation.getArgument(1, String.class)));
            when(callerGuard.requireTenant(any(), anyString())).thenReturn(TENANT);
        }

        private void authorize() {
            authorize(CALLER, MEMBER, DataScope.TENANT);
        }

        private void authorize(DataScope scope) {
            authorize(CALLER, MEMBER, scope);
        }

        private void authorize(AuthenticatedCaller caller, MemberId member) {
            authorize(caller, member, DataScope.TENANT);
        }

        private void authorize(AuthenticatedCaller caller, MemberId member, DataScope scope) {
            when(access.access(caller, AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ))
                    .thenReturn(new CustomerAccessResolver.CustomerAccess(TENANT, member,
                            new CustomerVisibility(scope, member,
                                    scope == DataScope.DEPARTMENT_SUBTREE ? Set.of(DEPARTMENT) : Set.of(),
                                    CustomerAccessMode.READ)));
            when(access.access(caller, AuthorityCatalog.CUSTOMERS_EXPORT,
                    CustomerAccessMode.OWNER_SCOPE)).thenReturn(
                    new CustomerAccessResolver.CustomerAccess(TENANT, member,
                            new CustomerVisibility(DataScope.TENANT, member, Set.of(),
                                    CustomerAccessMode.OWNER_SCOPE)));
        }

        private void readableFields() {
            fields.forEach(field -> accesses.put(field.getCode(),
                    new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.NONE)));
            when(support.fields(TENANT)).thenReturn(fields);
            when(support.accesses(eq(TENANT), any(MemberId.class))).thenReturn(accesses);
        }

        private void addFields(List<FieldDefinition> additional) {
            fields.addAll(additional);
        }

        private CustomerCsvExportServiceImpl service() {
            return new CustomerCsvExportServiceImpl(customers, views, callerGuard, access,
                    subordinates, support, admission,
                    new QueryServiceTemplate("test-app", transactionManager));
        }
    }

    private static final class CountingTransactionManager implements PlatformTransactionManager {
        private final AtomicInteger transactions = new AtomicInteger();

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            transactions.incrementAndGet();
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }

        private int transactionCount() {
            return transactions.get();
        }
    }
}
