package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.application.security.ContactAccessResolver;
import io.github.archetom.crm.application.security.ContractAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.InvoiceAccessResolver;
import io.github.archetom.crm.application.security.LeadAccessResolver;
import io.github.archetom.crm.application.security.OpportunityAccessResolver;
import io.github.archetom.crm.application.security.ReceivableAccessResolver;
import io.github.archetom.crm.domain.entity.Contact;
import io.github.archetom.crm.domain.entity.Contract;
import io.github.archetom.crm.domain.entity.Customer;
import io.github.archetom.crm.domain.entity.Invoice;
import io.github.archetom.crm.domain.entity.Lead;
import io.github.archetom.crm.domain.entity.Opportunity;
import io.github.archetom.crm.domain.entity.Receivable;
import io.github.archetom.crm.domain.entity.ReceivablePlan;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.ContactStatus;
import io.github.archetom.crm.domain.model.ContractStatus;
import io.github.archetom.crm.domain.model.CustomerStatus;
import io.github.archetom.crm.domain.model.InvoiceStatus;
import io.github.archetom.crm.domain.model.LeadStatus;
import io.github.archetom.crm.domain.model.OpportunityStatus;
import io.github.archetom.crm.domain.model.ReceivablePlanStatus;
import io.github.archetom.crm.domain.model.ReceivableStatus;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.InvoiceRepository;
import io.github.archetom.crm.domain.repository.LeadRepository;
import io.github.archetom.crm.domain.repository.OpportunityRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.ReceivablePlanRepository;
import io.github.archetom.crm.domain.repository.ReceivableRepository;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.InvoiceId;
import io.github.archetom.crm.domain.valueobject.LeadId;
import io.github.archetom.crm.domain.valueobject.OpportunityId;
import io.github.archetom.crm.domain.valueobject.ReceivableId;
import io.github.archetom.crm.domain.valueobject.ReceivablePlanId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrmAttachmentTargetAccessSupportTest {
    private static final TenantId TENANT = new TenantId(1L);

    @Test
    void locksAllEightCrmRootsAndPreservesTheirNonDeleteLifecycleStates() {
        Targets targets = targets();

        for (AttachmentTargetAccess access : targets.accesses()) {
            AttachmentTarget target = new AttachmentTarget(access.targetType(), 9L);
            assertTrue(access.lockForWorker(TENANT, target).attachmentsPermitted(),
                    () -> access.targetType() + " should permit attachments");
        }
    }

    @Test
    void poolStatesPauseCustomerAndContactWithoutTreatingThemAsDeleted() {
        Targets targets = targets();
        when(targets.customer().getStatus()).thenReturn(CustomerStatus.IN_POOL);
        when(targets.contact().getStatus()).thenReturn(ContactStatus.IN_POOL);

        AttachmentTargetLifecycle customer = targets.accesses().getFirst().lockForWorker(TENANT,
                new AttachmentTarget(AttachmentTargetType.CUSTOMER, 9L));
        AttachmentTargetLifecycle contact = targets.accesses().get(1).lockForWorker(TENANT,
                new AttachmentTarget(AttachmentTargetType.CONTACT, 9L));

        assertFalse(customer.attachmentsPermitted());
        assertTrue(customer.attachmentsPaused());
        assertFalse(contact.attachmentsPermitted());
        assertTrue(contact.attachmentsPaused());
    }

    private static Targets targets() {
        OrganizationTenantRepository tenantRepository = mock(OrganizationTenantRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ContactRepository contactRepository = mock(ContactRepository.class);
        LeadRepository leadRepository = mock(LeadRepository.class);
        OpportunityRepository opportunityRepository = mock(OpportunityRepository.class);
        ContractRepository contractRepository = mock(ContractRepository.class);
        InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
        ReceivableRepository receivableRepository = mock(ReceivableRepository.class);
        ReceivablePlanRepository receivablePlanRepository = mock(ReceivablePlanRepository.class);
        Customer customer = mock(Customer.class);
        Contact contact = mock(Contact.class);
        Lead lead = mock(Lead.class);
        Opportunity opportunity = mock(Opportunity.class);
        Contract contract = mock(Contract.class);
        Invoice invoice = mock(Invoice.class);
        Receivable receivable = mock(Receivable.class);
        ReceivablePlan receivablePlan = mock(ReceivablePlan.class);
        when(tenantRepository.lockForTenantChanges(TENANT)).thenReturn(true);
        when(customerRepository.findByIdForAttachmentMutation(eq(TENANT), any(CustomerId.class)))
                .thenReturn(Optional.of(customer));
        when(contactRepository.findByIdForAttachmentMutation(eq(TENANT), any(ContactId.class)))
                .thenReturn(Optional.of(contact));
        when(leadRepository.findByIdForAttachmentMutation(eq(TENANT), any(LeadId.class)))
                .thenReturn(Optional.of(lead));
        when(opportunityRepository.findByIdForAttachmentMutation(eq(TENANT), any(OpportunityId.class)))
                .thenReturn(Optional.of(opportunity));
        when(contractRepository.findByIdForAttachmentMutation(eq(TENANT), any(ContractId.class)))
                .thenReturn(Optional.of(contract));
        when(invoiceRepository.findByIdForAttachmentMutation(eq(TENANT), any(InvoiceId.class)))
                .thenReturn(Optional.of(invoice));
        when(receivableRepository.findByIdForAttachmentMutation(eq(TENANT), any(ReceivableId.class)))
                .thenReturn(Optional.of(receivable));
        when(receivablePlanRepository.findByIdForAttachmentMutation(eq(TENANT), any(ReceivablePlanId.class)))
                .thenReturn(Optional.of(receivablePlan));
        when(customer.getStatus()).thenReturn(CustomerStatus.ACTIVE);
        when(contact.getStatus()).thenReturn(ContactStatus.ACTIVE);
        when(lead.getStatus()).thenReturn(LeadStatus.CONVERTED);
        when(opportunity.getStatus()).thenReturn(OpportunityStatus.ACTIVE);
        when(contract.getStatus()).thenReturn(ContractStatus.VOIDED);
        when(invoice.getStatus()).thenReturn(InvoiceStatus.ISSUED);
        when(receivable.getStatus()).thenReturn(ReceivableStatus.ACTIVE);
        when(receivablePlan.getStatus()).thenReturn(ReceivablePlanStatus.ACTIVE);

        CrmAttachmentTargetAccessSupport support = new CrmAttachmentTargetAccessSupport(tenantRepository,
                mock(CustomerAccessResolver.class), customerRepository, mock(ContactAccessResolver.class), contactRepository,
                mock(LeadAccessResolver.class), leadRepository, mock(OpportunityAccessResolver.class), opportunityRepository,
                mock(ContractAccessResolver.class), contractRepository, mock(InvoiceAccessResolver.class), invoiceRepository,
                mock(ReceivableAccessResolver.class), receivableRepository, receivablePlanRepository);
        return new Targets(customer, contact, List.of(
                new CustomerAttachmentTargetAccess(support), new ContactAttachmentTargetAccess(support),
                new LeadAttachmentTargetAccess(support), new OpportunityAttachmentTargetAccess(support),
                new ContractAttachmentTargetAccess(support), new InvoiceAttachmentTargetAccess(support),
                new ReceivableAttachmentTargetAccess(support), new ReceivablePlanAttachmentTargetAccess(support)));
    }

    private record Targets(Customer customer, Contact contact, List<AttachmentTargetAccess> accesses) { }
}
