package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.LeadConvertRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.ContactAccessResolver;
import io.github.archetom.crm.application.security.ContactAccessResolver.ContactAccess;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver.CustomerAccess;
import io.github.archetom.crm.application.security.LeadAccessResolver;
import io.github.archetom.crm.application.security.LeadAccessResolver.LeadAccess;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.entity.Contact;
import io.github.archetom.crm.domain.entity.Customer;
import io.github.archetom.crm.domain.entity.Lead;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.model.ContactAccessMode;
import io.github.archetom.crm.domain.model.ContactVisibility;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.CustomerVisibility;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.LeadAccessMode;
import io.github.archetom.crm.domain.model.LeadVisibility;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.LeadFieldCatalog;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.LeadRepository;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LeadConversionServiceImplTest {

    private final TenantId tenantId = new TenantId(9L);
    private final MemberId memberId = new MemberId(7L);
    private final AuthenticatedCaller caller = mock(AuthenticatedCaller.class);
    private final LeadRepository leadRepository = mock(LeadRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final ContactRepository contactRepository = mock(ContactRepository.class);
    private final LeadAccessResolver leadAccessResolver = mock(LeadAccessResolver.class);
    private final CustomerAccessResolver customerAccessResolver = mock(CustomerAccessResolver.class);
    private final ContactAccessResolver contactAccessResolver = mock(ContactAccessResolver.class);
    private final LeadApplicationSupport leadSupport = mock(LeadApplicationSupport.class);
    private final CustomerApplicationSupport customerSupport = mock(CustomerApplicationSupport.class);
    private final ContactApplicationSupport contactSupport = mock(ContactApplicationSupport.class);
    private final CustomizationObjectLock objectLock = mock(CustomizationObjectLock.class);
    private final CommandServiceTemplate commandTemplate = mock(CommandServiceTemplate.class);
    private LeadConversionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LeadConversionServiceImpl(leadRepository, customerRepository,
                contactRepository, leadAccessResolver, customerAccessResolver,
                contactAccessResolver, leadSupport, customerSupport, contactSupport,
                objectLock, commandTemplate);
        when(commandTemplate.execute(eq(UseCaseOperation.LEAD_CONVERT), any()))
                .thenAnswer(invocation -> {
                    ServiceOperation<?> operation = invocation.getArgument(1);
                    operation.validate();
                    operation.execute();
                    return new Result<>();
                });
    }

    @Test
    void missingLeadConvertAuthorityRejectsBeforeAnyRepositoryAccess() {
        NonRetryableApplicationException denied = new NonRetryableApplicationException(
                ApplicationErrorCode.ACCESS_DENIED);
        when(leadAccessResolver.access(caller, AuthorityCatalog.LEADS_CONVERT,
                LeadAccessMode.WRITE)).thenThrow(denied);

        assertThrows(NonRetryableApplicationException.class,
                () -> service.convert(caller, 1L, new LeadConvertRequest()));

        verifyNoInteractions(leadRepository, customerRepository, contactRepository);
        verifyNoInteractions(customerAccessResolver, contactAccessResolver);
    }

    @Test
    void maskedCopiedFieldRejectsBeforeCreatingTargets() {
        stubWriteAccess();
        Lead lead = sourceLead();
        when(leadRepository.findVisibleById(eq(tenantId), any(), any()))
                .thenReturn(Optional.of(lead));
        when(leadSupport.fields(tenantId)).thenReturn(List.of());
        when(leadSupport.accesses(tenantId, memberId)).thenReturn(Map.of());
        doThrow(new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED))
                .when(leadSupport).requireReadableUnmasked(anyMap(), eq(LeadFieldCatalog.NAME));

        assertThrows(NonRetryableApplicationException.class,
                () -> service.convert(caller, 1L, new LeadConvertRequest()));

        verifyNoInteractions(customerRepository, contactRepository);
    }

    @Test
    void downstreamContactFailureIsNotSwallowedAndLeadIsNotFinalized() {
        stubWriteAccess();
        Lead lead = sourceLead();
        when(leadRepository.findVisibleById(eq(tenantId), any(), any()))
                .thenReturn(Optional.of(lead));
        when(leadSupport.fields(tenantId)).thenReturn(List.of());
        when(leadSupport.accesses(tenantId, memberId)).thenReturn(Map.of());
        OrganizationMembership owner = mock(OrganizationMembership.class);
        when(owner.getMemberId()).thenReturn(memberId);
        when(customerSupport.requireActiveMember(tenantId, memberId)).thenReturn(owner);
        when(customerSupport.fields(tenantId)).thenReturn(List.of());
        when(customerSupport.fieldsByCode(List.of())).thenReturn(Map.of());
        when(customerSupport.accesses(tenantId, memberId)).thenReturn(Map.of());
        when(customerSupport.customFields(any(), anyCollection(), eq(true), anyMap(), anyMap()))
                .thenReturn(List.of());
        when(contactSupport.fields(tenantId)).thenReturn(List.of());
        when(contactSupport.fieldsByCode(List.of())).thenReturn(Map.of());
        when(contactSupport.accesses(tenantId, memberId)).thenReturn(Map.of());
        when(contactSupport.customFields(any(), anyCollection(), eq(true), anyMap(), anyMap()))
                .thenReturn(List.of());
        Customer savedCustomer = mock(Customer.class);
        when(savedCustomer.getId()).thenReturn(new CustomerId(41L));
        when(customerRepository.save(eq(tenantId), any(Customer.class), anyCollection()))
                .thenReturn(savedCustomer);
        RuntimeException failure = new RuntimeException("contact persistence failed");
        when(contactRepository.save(eq(tenantId), any(Contact.class), anyCollection()))
                .thenThrow(failure);

        assertThrows(RuntimeException.class,
                () -> service.convert(caller, 1L, new LeadConvertRequest()));

        verify(leadRepository, never()).save(eq(tenantId), any(Lead.class), anyCollection());
    }

    private void stubWriteAccess() {
        when(leadAccessResolver.access(caller, AuthorityCatalog.LEADS_CONVERT,
                LeadAccessMode.WRITE)).thenReturn(new LeadAccess(tenantId, memberId,
                        new LeadVisibility(DataScope.TENANT, memberId, Set.of(),
                                LeadAccessMode.WRITE)));
        when(customerAccessResolver.access(caller, AuthorityCatalog.CUSTOMERS_WRITE,
                CustomerAccessMode.WRITE)).thenReturn(new CustomerAccess(tenantId, memberId,
                        new CustomerVisibility(DataScope.TENANT, memberId, Set.of(),
                                CustomerAccessMode.WRITE)));
        when(contactAccessResolver.access(caller, AuthorityCatalog.CONTACTS_WRITE,
                ContactAccessMode.WRITE)).thenReturn(new ContactAccess(tenantId, memberId,
                        new ContactVisibility(DataScope.TENANT, memberId, Set.of(),
                                ContactAccessMode.WRITE)));
    }

    private Lead sourceLead() {
        return Lead.create(tenantId, memberId, "Source lead", null, null, null,
                null, null, null, null, List.of(), List.of());
    }
}
