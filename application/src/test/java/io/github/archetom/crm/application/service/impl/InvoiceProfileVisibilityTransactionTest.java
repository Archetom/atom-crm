package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.InvoiceProfileRequest;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.ContractAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver.CustomerAccess;
import io.github.archetom.crm.application.security.InvoiceAccessResolver;
import io.github.archetom.crm.application.security.InvoiceAccessResolver.InvoiceAccess;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.attachment.AttachmentParentLifecycle;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.domain.entity.Customer;
import io.github.archetom.crm.domain.entity.InvoiceProfile;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.CustomerVisibility;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.InvoiceAccessMode;
import io.github.archetom.crm.domain.model.InvoiceTitleType;
import io.github.archetom.crm.domain.model.InvoiceVisibility;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.InvoiceProfileRepository;
import io.github.archetom.crm.domain.repository.InvoiceRepository;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.InvoiceProfileId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceProfileVisibilityTransactionTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId MEMBER = new MemberId(7L);
    private static final CustomerId CUSTOMER = new CustomerId(5L);
    private static final AuthenticatedCaller CALLER =
            new AuthenticatedCaller(MEMBER.getValue(), TENANT.getValue(), Set.of());

    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final InvoiceProfileRepository profileRepository =
            mock(InvoiceProfileRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final ContractRepository contractRepository = mock(ContractRepository.class);
    private final InvoiceAccessResolver invoiceAccessResolver =
            mock(InvoiceAccessResolver.class);
    private final CustomerAccessResolver customerAccessResolver =
            mock(CustomerAccessResolver.class);
    private final ContractAccessResolver contractAccessResolver =
            mock(ContractAccessResolver.class);
    private final InvoiceApplicationSupport support = mock(InvoiceApplicationSupport.class);
    private final CustomizationObjectLock objectLock = mock(CustomizationObjectLock.class);

    private InvoiceAccess invoiceAccess;
    private CustomerAccess customerAccess;

    @BeforeEach
    void setUp() {
        invoiceAccess = new InvoiceAccess(TENANT, MEMBER, new InvoiceVisibility(
                DataScope.TENANT, MEMBER, Set.of(), InvoiceAccessMode.READ));
        customerAccess = new CustomerAccess(TENANT, MEMBER, new CustomerVisibility(
                DataScope.TENANT, MEMBER, Set.of(), CustomerAccessMode.READ));
        when(customerAccessResolver.access(any(), any(), any())).thenReturn(customerAccess);
        when(customerRepository.findVisibleById(TENANT, CUSTOMER,
                customerAccess.visibility())).thenReturn(Optional.of(mock(Customer.class)));
    }

    @Test
    void profileQueryRechecksCustomerAfterTheReadTransactionStarts() {
        TestTransactionManager transactions = new TestTransactionManager();
        when(invoiceAccessResolver.access(CALLER, "invoices:read", InvoiceAccessMode.READ))
                .thenReturn(invoiceAccess);
        when(customerRepository.findVisibleById(TENANT, CUSTOMER,
                customerAccess.visibility())).thenAnswer(invocation -> {
                    assertTrue(transactions.active());
                    return Optional.of(mock(Customer.class));
                });
        when(profileRepository.findByCustomer(TENANT, CUSTOMER,
                InvoiceProfile.MAX_ACTIVE_PER_CUSTOMER)).thenReturn(List.of());
        InvoiceQueryServiceImpl service = new InvoiceQueryServiceImpl(
                invoiceRepository, profileRepository, customerRepository, contractRepository,
                null, invoiceAccessResolver, customerAccessResolver, contractAccessResolver, null, support,
                new QueryServiceTemplate("test-app", transactions));

        var result = service.profiles(CALLER, CUSTOMER.getValue());

        assertTrue(result.isSuccess());
        verify(customerRepository).findVisibleById(TENANT, CUSTOMER, customerAccess.visibility());
        assertFalse(transactions.active());
    }

    @Test
    void profileCreateRechecksCustomerAfterTheWriteTransactionStarts() {
        TestTransactionManager transactions = new TestTransactionManager();
        invoiceAccess = new InvoiceAccess(TENANT, MEMBER, new InvoiceVisibility(
                DataScope.TENANT, MEMBER, Set.of(), InvoiceAccessMode.WRITE));
        when(invoiceAccessResolver.access(CALLER, "invoices:write", InvoiceAccessMode.WRITE))
                .thenReturn(invoiceAccess);
        when(customerRepository.findVisibleById(TENANT, CUSTOMER,
                customerAccess.visibility())).thenAnswer(invocation -> {
                    assertTrue(transactions.active());
                    return Optional.of(mock(Customer.class));
                });
        when(support.parse(InvoiceTitleType.class, "INDIVIDUAL", "Invoice title type"))
                .thenReturn(InvoiceTitleType.INDIVIDUAL);
        when(profileRepository.save(any(), any())).thenAnswer(invocation -> {
            InvoiceProfile profile = invocation.getArgument(1);
            LocalDateTime now = LocalDateTime.of(2026, 7, 18, 9, 0);
            profile.onPersisted(new InvoiceProfileId(11L), 0L, now, now);
            return profile;
        });
        InvoiceCommandServiceImpl service = new InvoiceCommandServiceImpl(
                invoiceRepository, profileRepository, contractRepository, customerRepository,
                invoiceAccessResolver, contractAccessResolver, customerAccessResolver, support,
                mock(AttachmentParentLifecycle.class), objectLock,
                new CommandServiceTemplate("test-app", transactions));

        var result = service.createProfile(CALLER, new InvoiceProfileRequest()
                .setCustomerId(CUSTOMER.getValue()).setTitleType("INDIVIDUAL")
                .setTitle("Personal"));

        assertTrue(result.isSuccess());
        verify(customerRepository).findVisibleById(TENANT, CUSTOMER, customerAccess.visibility());
        assertFalse(transactions.active());
    }
}
