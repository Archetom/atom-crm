package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.EnterpriseDataAdoptRequest;
import io.github.archetom.crm.api.dto.request.EnterpriseDataAdoptionPreviewRequest;
import io.github.archetom.crm.api.dto.request.EnterpriseDataIdentitySelectionRequest;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.port.out.enterprisedata.CustomerRegisteredIdentityStore;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAdoptionPolicy;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAdoptionPolicyResolver;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAdoptionProvenanceStore;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAttribution;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataCapabilitySnapshot;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataObservation;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataPreview;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataPreviewStore;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataReadPolicy;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataReadPolicyResolver;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSource;
import io.github.archetom.crm.application.port.out.enterprisedata.RegisteredAddressObservation;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.domain.entity.Customer;
import io.github.archetom.crm.domain.entity.CustomerAddress;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.CustomerVisibility;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.EnterpriseDataAdoptableFact;
import io.github.archetom.crm.domain.model.EnterpriseDataAttributionContext;
import io.github.archetom.crm.domain.model.EnterpriseDataSourceKind;
import io.github.archetom.crm.domain.model.EnterpriseDataUsageMode;
import io.github.archetom.crm.domain.model.EnterpriseQueryMode;
import io.github.archetom.crm.domain.model.IdentifierStanding;
import io.github.archetom.crm.domain.model.IdentityScope;
import io.github.archetom.crm.domain.model.RegisteredEntityStatus;
import io.github.archetom.crm.domain.model.RegistryScheme;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifier;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierType;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnterpriseDataAdoptionServiceImplTest {
    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId MEMBER = new MemberId(7L);
    private static final CustomerId CUSTOMER_ID = new CustomerId(41L);
    private static final AuthenticatedCaller CALLER = new AuthenticatedCaller(7L, 9L,
            Set.of(AuthorityCatalog.ENTERPRISE_DATA_READ, AuthorityCatalog.CUSTOMERS_WRITE));
    private static final Instant NOW = Instant.parse("2026-07-23T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void adoptsOnlyARefreshedExactPreviewAndPersistsValueFreeProvenance() {
        Fixture fixture = fixture();
        when(fixture.source.capabilities(TENANT)).thenAnswer(invocation -> {
            assertFalse(fixture.transactions.active());
            return capability();
        });
        when(fixture.source.findByRegisteredIdentifier(eq(TENANT), any())).thenAnswer(invocation -> {
            assertFalse(fixture.transactions.active());
            return Optional.of(observation("Synthetic Registry Labs Ltd"));
        });
        Customer customer = customer();
        when(fixture.customers.findVisibleById(eq(TENANT), eq(CUSTOMER_ID), any()))
                .thenReturn(Optional.of(customer));
        when(fixture.customers.findVisibleByIdForUpdate(eq(TENANT), eq(CUSTOMER_ID), any()))
                .thenReturn(Optional.of(customer));
        when(fixture.identities.reserve(eq(TENANT), eq(CUSTOMER_ID), any())).thenReturn(List.of(
                new CustomerRegisteredIdentityStore.RegisteredIdentityAssociation(identifier().key(), 91L)));
        when(fixture.customers.save(eq(TENANT), eq(customer), any())).thenAnswer(invocation -> {
            customer.onPersisted(CUSTOMER_ID, 4L, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC),
                    LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
            return customer;
        });

        Result<io.github.archetom.crm.application.vo.EnterpriseDataAdoptionPreviewVO> preview =
                fixture.service.preview(CALLER, previewRequest());

        assertTrue(preview.isSuccess());
        assertEquals("ADOPT_SELECTED_FACTS", preview.getData().observation().usageMode());
        assertEquals(3, preview.getData().diffs().size());
        Result<io.github.archetom.crm.application.vo.EnterpriseDataAdoptionVO> adopted = fixture.service.adopt(
                CALLER, adoptRequest(preview.getData().previewReference()));

        assertTrue(adopted.isSuccess());
        assertEquals(CUSTOMER_ID.getValue(), adopted.getData().customerId());
        assertEquals(4L, adopted.getData().customerVersion());
        assertEquals("ADOPTION_CONFIRMATION", adopted.getData().attribution().context());
        assertEquals("Synthetic Registry Labs Ltd", customer.getName());
        assertEquals("1 Contract Test Way", customer.getAddresses().getFirst().addressLine());
        verify(fixture.objectLock).acquire(TENANT,
                io.github.archetom.crm.domain.model.BusinessObjectType.CUSTOMER);
        verify(fixture.identities).reserve(eq(TENANT), eq(CUSTOMER_ID),
                eq(List.of(identifier().key())));
        ArgumentCaptor<EnterpriseDataAdoptionProvenanceStore.Adoption> evidence =
                ArgumentCaptor.forClass(EnterpriseDataAdoptionProvenanceStore.Adoption.class);
        verify(fixture.provenance).append(evidence.capture());
        assertEquals(91L, evidence.getValue().primaryIdentityAssociationId());
        assertEquals(3, evidence.getValue().selections().size());
        assertFalse(evidence.getValue().toString().contains("Synthetic Registry Labs Ltd"));
        assertFalse(evidence.getValue().toString().contains("1 Contract Test Way"));
    }

    @Test
    void failsClosedBeforeAnyProviderCallWhenPolicyDoesNotPermitAdoption() {
        Fixture fixture = fixture();
        when(fixture.adoptionPolicies.resolve(TENANT)).thenReturn(EnterpriseDataAdoptionPolicy.unavailable());

        Result<?> result = fixture.service.preview(CALLER, previewRequest());

        assertError(result, ApplicationErrorCode.ENTERPRISE_DATA_UNAVAILABLE);
        verify(fixture.source, never()).capabilities(any());
        verify(fixture.source, never()).findByRegisteredIdentifier(any(), any());
    }

    @Test
    void normalizesAdapterRuntimeFailuresWithoutExposingProviderText() {
        Fixture fixture = fixture();
        when(fixture.source.capabilities(TENANT)).thenReturn(capability());
        String providerOnlyCanary = "B23_ADOPTION_RAW_PROVIDER_CANARY";
        when(fixture.source.findByRegisteredIdentifier(eq(TENANT), any()))
                .thenThrow(new IllegalStateException(providerOnlyCanary));

        Result<?> result = fixture.service.preview(CALLER, previewRequest());

        assertError(result, ApplicationErrorCode.ENTERPRISE_DATA_UNAVAILABLE);
        assertFalse(result.toString().contains(providerOnlyCanary));
    }

    @Test
    void refusesAdoptionWhenTheRefreshedFactDiffersAndDoesNotWrite() {
        Fixture fixture = fixture();
        when(fixture.source.capabilities(TENANT)).thenReturn(capability());
        when(fixture.source.findByRegisteredIdentifier(eq(TENANT), any()))
                .thenReturn(Optional.of(observation("Synthetic Registry Labs Ltd")))
                .thenReturn(Optional.of(observation("Changed During Preview Ltd")));
        Customer customer = customer();
        when(fixture.customers.findVisibleById(eq(TENANT), eq(CUSTOMER_ID), any()))
                .thenReturn(Optional.of(customer));

        Result<io.github.archetom.crm.application.vo.EnterpriseDataAdoptionPreviewVO> preview =
                fixture.service.preview(CALLER, previewRequest());
        Result<?> result = fixture.service.adopt(CALLER, adoptRequest(preview.getData().previewReference()));

        assertError(result, ApplicationErrorCode.VERSION_CONFLICT);
        verify(fixture.identities, never()).reserve(any(), any(), any());
        verify(fixture.customers, never()).save(any(), any(), any());
        verify(fixture.provenance, never()).append(any());
        assertTrue(fixture.previews.holdsPreview(),
                "a refused adoption adopted nothing, so its one-shot reference must survive");
    }

    /**
     * The preview reference is validated before the transaction opens and spent only after the
     * adoption succeeds.
     *
     * <p>Spending it during validation would burn it on every attempt that goes on to fail: the
     * store is process-local and outside the transaction, so a rollback cannot put it back, and the
     * caller would be told to redo the whole preview even though nothing was adopted. The
     * single-use guarantee still has to hold, so both halves are pinned — a refused adoption keeps
     * the reference (asserted above), and a successful one consumes it.</p>
     */
    @Test
    void aSuccessfulAdoptionSpendsThePreviewReference() {
        Fixture fixture = fixture();
        when(fixture.source.capabilities(TENANT)).thenReturn(capability());
        when(fixture.source.findByRegisteredIdentifier(eq(TENANT), any()))
                .thenReturn(Optional.of(observation("Synthetic Registry Labs Ltd")));
        Customer customer = customer();
        when(fixture.customers.findVisibleById(eq(TENANT), eq(CUSTOMER_ID), any()))
                .thenReturn(Optional.of(customer));
        when(fixture.customers.findVisibleByIdForUpdate(eq(TENANT), eq(CUSTOMER_ID), any()))
                .thenReturn(Optional.of(customer));
        when(fixture.identities.reserve(eq(TENANT), eq(CUSTOMER_ID), any())).thenReturn(List.of(
                new CustomerRegisteredIdentityStore.RegisteredIdentityAssociation(identifier().key(), 91L)));
        when(fixture.customers.save(eq(TENANT), eq(customer), any())).thenAnswer(invocation -> {
            customer.onPersisted(CUSTOMER_ID, 4L, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC),
                    LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
            return customer;
        });

        Result<io.github.archetom.crm.application.vo.EnterpriseDataAdoptionPreviewVO> preview =
                fixture.service.preview(CALLER, previewRequest());
        Result<?> adopted = fixture.service.adopt(CALLER,
                adoptRequest(preview.getData().previewReference()));

        assertTrue(adopted.isSuccess());
        assertFalse(fixture.previews.holdsPreview(), "an adopted reference must not adopt twice");
    }

    private Fixture fixture() {
        EnterpriseDataSource source = mock(EnterpriseDataSource.class);
        EnterpriseDataReadPolicyResolver readPolicies = mock(EnterpriseDataReadPolicyResolver.class);
        EnterpriseDataAdoptionPolicyResolver adoptionPolicies = mock(EnterpriseDataAdoptionPolicyResolver.class);
        CustomerRegisteredIdentityStore identities = mock(CustomerRegisteredIdentityStore.class);
        EnterpriseDataAdoptionProvenanceStore provenance = mock(EnterpriseDataAdoptionProvenanceStore.class);
        OrganizationAccessGuard membership = mock(OrganizationAccessGuard.class);
        CustomerAccessResolver customersAccess = mock(CustomerAccessResolver.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        CustomerApplicationSupport customerSupport = mock(CustomerApplicationSupport.class);
        CustomizationObjectLock objectLock = mock(CustomizationObjectLock.class);
        TestPreviewStore previews = new TestPreviewStore();
        when(membership.requireActiveMembership(CALLER, AuthorityCatalog.ENTERPRISE_DATA_READ))
                .thenReturn(TENANT);
        when(customersAccess.access(CALLER, AuthorityCatalog.CUSTOMERS_WRITE, CustomerAccessMode.WRITE))
                .thenReturn(new CustomerAccessResolver.CustomerAccess(TENANT, MEMBER,
                        new CustomerVisibility(DataScope.TENANT, MEMBER, Set.of(), CustomerAccessMode.WRITE)));
        when(readPolicies.resolve(TENANT)).thenReturn(readPolicy());
        when(adoptionPolicies.resolve(TENANT)).thenReturn(adoptionPolicy());
        when(customerSupport.accesses(TENANT, MEMBER)).thenReturn(Map.of(
                new io.github.archetom.crm.domain.valueobject.FieldCode(CustomerFieldCatalog.NAME),
                io.github.archetom.crm.domain.model.EffectiveFieldAccess.owner(),
                new io.github.archetom.crm.domain.valueobject.FieldCode(CustomerFieldCatalog.ADDRESSES),
                io.github.archetom.crm.domain.model.EffectiveFieldAccess.owner()));
        when(customerSupport.fields(TENANT)).thenReturn(List.of());
        // One manager behind both templates: the external source must be consulted while neither
        // the read nor the write boundary is open, and a shared flag states exactly that.
        TestTransactionManager transactions = new TestTransactionManager();
        EnterpriseDataAdoptionServiceImpl service = new EnterpriseDataAdoptionServiceImpl(source,
                readPolicies, adoptionPolicies, previews, identities, provenance, membership, customersAccess,
                customers, customerSupport, objectLock,
                new QueryServiceTemplate("test-app", transactions),
                new CommandServiceTemplate("test-app", transactions), CLOCK);
        return new Fixture(service, source, adoptionPolicies, identities, provenance, customers,
                objectLock, previews, transactions);
    }

    private EnterpriseDataReadPolicy readPolicy() {
        return new EnterpriseDataReadPolicy(true, EnterpriseDataSourceKind.SYNTHETIC_FIXTURE,
                EnterpriseDataUsageMode.ADOPT_SELECTED_FACTS, Duration.ofMinutes(5),
                attribution(EnterpriseDataAttributionContext.SEARCH_RESULT),
                attribution(EnterpriseDataAttributionContext.DETAIL));
    }

    private EnterpriseDataAdoptionPolicy adoptionPolicy() {
        return new EnterpriseDataAdoptionPolicy(true, EnterpriseDataSourceKind.SYNTHETIC_FIXTURE,
                EnterpriseDataUsageMode.ADOPT_SELECTED_FACTS, Duration.ofMinutes(5), Duration.ofDays(30),
                "synthetic-source-v1", "synthetic-evidence-v1", "policy-v1", "attribution-v1",
                "mapping-v1", Set.of(EnterpriseDataAdoptableFact.REGISTERED_IDENTITY,
                        EnterpriseDataAdoptableFact.REGISTERED_NAME,
                        EnterpriseDataAdoptableFact.REGISTERED_ADDRESS),
                attribution(EnterpriseDataAttributionContext.PREVIEW),
                attribution(EnterpriseDataAttributionContext.ADOPTION_CONFIRMATION));
    }

    private EnterpriseDataAttribution attribution(EnterpriseDataAttributionContext context) {
        return new EnterpriseDataAttribution(context, "Synthetic fixture authority", null, null);
    }

    private EnterpriseDataCapabilitySnapshot capability() {
        return new EnterpriseDataCapabilitySnapshot(true,
                Set.of(EnterpriseQueryMode.EXACT_REGISTERED_IDENTIFIER), Set.of(),
                Set.of(new RegisteredIdentifierType(IdentityScope.GLOBAL_LEI_SYSTEM, RegistryScheme.LEI)), 1);
    }

    private EnterpriseDataObservation observation(String name) {
        RegisteredIdentifier identifier = identifier();
        return new EnterpriseDataObservation(identifier, List.of(identifier),
                new io.github.archetom.crm.domain.valueobject.LegalJurisdiction("GB"), name,
                RegisteredEntityStatus.ACTIVE, null, null,
                new RegisteredAddressObservation("GB", null, "Fixture City", "1 Contract Test Way", "ZZ1 1ZZ"),
                EnterpriseDataSourceKind.SYNTHETIC_FIXTURE, false, NOW.minusSeconds(60), "synthetic-v1");
    }

    private RegisteredIdentifier identifier() {
        return new RegisteredIdentifier(IdentityScope.GLOBAL_LEI_SYSTEM, RegistryScheme.LEI,
                "0000SYNTHETICFIXTU42", IdentifierStanding.CURRENT);
    }

    private Customer customer() {
        Customer customer = Customer.create(TENANT, MEMBER, "Pre-existing Customer", null, null, null,
                null, null, List.of(new CustomerAddress("registered", "Registered address", "GB", null,
                        "Fixture City", "ZZ0 0ZZ", "Old Contract Test Way", null, null, false)), List.of());
        customer.onPersisted(CUSTOMER_ID, 3L, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC),
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        return customer;
    }

    private EnterpriseDataAdoptionPreviewRequest previewRequest() {
        return new EnterpriseDataAdoptionPreviewRequest().setCustomerId(CUSTOMER_ID.getValue())
                .setCustomerVersion(3L).setIdentityScope("GLOBAL_LEI_SYSTEM")
                .setRegistryScheme("LEI").setValue("0000SYNTHETICFIXTU42")
                .setAddressSlotKey("registered");
    }

    private EnterpriseDataAdoptRequest adoptRequest(String previewReference) {
        return new EnterpriseDataAdoptRequest().setPreviewReference(previewReference).setCustomerVersion(3L)
                .setFacts(List.of("REGISTERED_IDENTITY", "REGISTERED_NAME", "REGISTERED_ADDRESS"))
                .setIdentities(List.of(new EnterpriseDataIdentitySelectionRequest()
                        .setIdentityScope("GLOBAL_LEI_SYSTEM").setRegistryScheme("LEI")));
    }

    private void assertError(Result<?> result, ApplicationErrorCode expected) {
        assertFalse(result.isSuccess());
        assertEquals(expected.getCode(), result.getErrorContext().fetchRootError()
                .getErrorCode().getErrorSpecific());
    }

    private record Fixture(EnterpriseDataAdoptionServiceImpl service, EnterpriseDataSource source,
                           EnterpriseDataAdoptionPolicyResolver adoptionPolicies,
                           CustomerRegisteredIdentityStore identities,
                           EnterpriseDataAdoptionProvenanceStore provenance,
                           CustomerRepository customers, CustomizationObjectLock objectLock,
                           TestPreviewStore previews, TestTransactionManager transactions) { }

    private static final class TestPreviewStore implements EnterpriseDataPreviewStore {
        private EnterpriseDataPreview preview;

        /** Whether the reference is still spendable, which is what a caller can retry with. */
        boolean holdsPreview() {
            return preview != null;
        }

        @Override public String issue(EnterpriseDataPreview value) {
            preview = value;
            return "preview-reference";
        }

        @Override public Optional<EnterpriseDataPreview> peek(
                TenantId tenantId, MemberId memberId, String reference, Instant now) {
            return matching(tenantId, memberId, reference, now);
        }

        @Override public Optional<EnterpriseDataPreview> claim(
                TenantId tenantId, MemberId memberId, String reference, Instant now) {
            Optional<EnterpriseDataPreview> claimed = matching(tenantId, memberId, reference, now);
            claimed.ifPresent(ignored -> preview = null);
            return claimed;
        }

        private Optional<EnterpriseDataPreview> matching(
                TenantId tenantId, MemberId memberId, String reference, Instant now) {
            if (!"preview-reference".equals(reference) || preview == null
                    || !preview.tenantId().sameValueAs(tenantId)
                    || !preview.memberId().sameValueAs(memberId)
                    || !now.isBefore(preview.expiresAt())) {
                return Optional.empty();
            }
            return Optional.of(preview);
        }
    }
}
