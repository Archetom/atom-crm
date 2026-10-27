package io.github.archetom.crm.application.security;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ContactAccessMode;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.LeadAccessMode;
import io.github.archetom.crm.domain.model.InvoiceAccessMode;
import io.github.archetom.crm.domain.model.OpportunityAccessMode;
import io.github.archetom.crm.domain.model.ReceivableAccessMode;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.LeadRepository;
import io.github.archetom.crm.domain.repository.InvoiceRepository;
import io.github.archetom.crm.domain.repository.OpportunityRepository;
import io.github.archetom.crm.domain.repository.OaRequestRepository;
import io.github.archetom.crm.domain.repository.ReceivableRepository;
import io.github.archetom.crm.domain.repository.ReceivablePlanRepository;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.LeadId;
import io.github.archetom.crm.domain.valueobject.InvoiceId;
import io.github.archetom.crm.domain.valueobject.OpportunityId;
import io.github.archetom.crm.domain.valueobject.OaRequestId;
import io.github.archetom.crm.domain.valueobject.ReceivableId;
import io.github.archetom.crm.domain.valueobject.ReceivablePlanId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Reapplies each business aggregate's own authority and data scope for generic workflows. */
@Component
@RequiredArgsConstructor
public class BusinessRecordAccessGuard {

    private static final int MAX_VISIBILITY_BATCH = 200;

    private final CustomerAccessResolver customerAccessResolver;
    private final ContactAccessResolver contactAccessResolver;
    private final LeadAccessResolver leadAccessResolver;
    private final OpportunityAccessResolver opportunityAccessResolver;
    private final ContractAccessResolver contractAccessResolver;
    private final InvoiceAccessResolver invoiceAccessResolver;
    private final ReceivableAccessResolver receivableAccessResolver;
    private final OaRequestAccessResolver oaRequestAccessResolver;
    private final CustomerRepository customerRepository;
    private final ContactRepository contactRepository;
    private final LeadRepository leadRepository;
    private final OpportunityRepository opportunityRepository;
    private final ContractRepository contractRepository;
    private final InvoiceRepository invoiceRepository;
    private final ReceivableRepository receivableRepository;
    private final ReceivablePlanRepository receivablePlanRepository;
    private final OaRequestRepository oaRequestRepository;

    public TenantId requireVisible(
            AuthenticatedCaller caller, BusinessObjectType objectType,
            Long subjectId, boolean write) {
        if (objectType == null || subjectId == null || subjectId <= 0) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Business record reference is invalid");
        }
        return switch (objectType) {
            case CUSTOMER -> customer(caller, subjectId, write);
            case CONTACT -> contact(caller, subjectId, write);
            case LEAD -> lead(caller, subjectId, write);
            case OPPORTUNITY -> opportunity(caller, subjectId, write);
            case CONTRACT -> contract(caller, subjectId, write);
            case INVOICE -> invoice(caller, subjectId, write);
            case RECEIVABLE -> receivable(caller, subjectId, write);
            case RECEIVABLE_PLAN -> receivablePlan(caller, subjectId, write);
            case OA_REQUEST -> oaRequest(caller, subjectId, write);
            default -> throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Business object workflow runtime is not available");
        };
    }

    /** Resolves visible record IDs in one bounded batch using the owning domain's read scope. */
    public Set<Long> visibleSubjectIds(
            AuthenticatedCaller caller, TenantId expectedTenant,
            BusinessObjectType objectType, Collection<Long> subjectIds) {
        if (expectedTenant == null || objectType == null || subjectIds == null
                || subjectIds.size() > MAX_VISIBILITY_BATCH) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Business record visibility batch is invalid");
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>(subjectIds);
        if (ids.size() != subjectIds.size()
                || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Business record visibility batch is invalid");
        }
        if (ids.isEmpty()) {
            return Set.of();
        }
        return switch (objectType) {
            case CUSTOMER -> visibleCustomers(caller, expectedTenant, ids);
            case CONTACT -> visibleContacts(caller, expectedTenant, ids);
            case LEAD -> visibleLeads(caller, expectedTenant, ids);
            case OPPORTUNITY -> visibleOpportunities(caller, expectedTenant, ids);
            case CONTRACT -> visibleContracts(caller, expectedTenant, ids);
            case INVOICE -> visibleInvoices(caller, expectedTenant, ids);
            case RECEIVABLE -> visibleReceivables(caller, expectedTenant, ids);
            case RECEIVABLE_PLAN -> visibleReceivablePlans(caller, expectedTenant, ids);
            case OA_REQUEST -> visibleOaRequests(caller, expectedTenant, ids);
            default -> throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Business object workflow runtime is not available");
        };
    }

    private Set<Long> visibleCustomers(
            AuthenticatedCaller caller, TenantId expectedTenant, Set<Long> ids) {
        var access = customerAccessResolver.access(caller, AuthorityCatalog.CUSTOMERS_READ,
                CustomerAccessMode.READ);
        requireSameTenant(expectedTenant, access.tenantId());
        return customerRepository.findVisibleIdsIn(access.tenantId(),
                        ids.stream().map(CustomerId::new).toList(), access.visibility(), ids.size());
    }

    private Set<Long> visibleContacts(
            AuthenticatedCaller caller, TenantId expectedTenant, Set<Long> ids) {
        var access = contactAccessResolver.access(caller, AuthorityCatalog.CONTACTS_READ,
                ContactAccessMode.READ);
        requireSameTenant(expectedTenant, access.tenantId());
        return contactRepository.findVisibleIdsIn(access.tenantId(),
                        ids.stream().map(ContactId::new).toList(), access.visibility(), ids.size());
    }

    private Set<Long> visibleLeads(
            AuthenticatedCaller caller, TenantId expectedTenant, Set<Long> ids) {
        var access = leadAccessResolver.access(caller, AuthorityCatalog.LEADS_READ,
                LeadAccessMode.READ);
        requireSameTenant(expectedTenant, access.tenantId());
        return leadRepository.findVisibleIdsIn(access.tenantId(),
                        ids.stream().map(LeadId::new).toList(), access.visibility(), ids.size());
    }

    private Set<Long> visibleOpportunities(
            AuthenticatedCaller caller, TenantId expectedTenant, Set<Long> ids) {
        var access = opportunityAccessResolver.access(caller,
                AuthorityCatalog.OPPORTUNITIES_READ, OpportunityAccessMode.READ);
        requireSameTenant(expectedTenant, access.tenantId());
        return opportunityRepository.findVisibleIdsIn(access.tenantId(),
                        ids.stream().map(OpportunityId::new).toList(),
                        access.visibility(), ids.size());
    }

    private Set<Long> visibleContracts(
            AuthenticatedCaller caller, TenantId expectedTenant, Set<Long> ids) {
        var access = contractAccessResolver.access(caller,
                AuthorityCatalog.CONTRACTS_READ, ContractAccessMode.READ);
        requireSameTenant(expectedTenant, access.tenantId());
        return contractRepository.findVisibleIdsIn(access.tenantId(),
                        ids.stream().map(ContractId::new).toList(),
                        access.visibility(), ids.size());
    }

    private Set<Long> visibleInvoices(
            AuthenticatedCaller caller, TenantId expectedTenant, Set<Long> ids) {
        var access = invoiceAccessResolver.access(caller,
                AuthorityCatalog.INVOICES_READ, InvoiceAccessMode.READ);
        requireSameTenant(expectedTenant, access.tenantId());
        return invoiceRepository.findVisibleIdsIn(access.tenantId(),
                        ids.stream().map(InvoiceId::new).toList(),
                        access.visibility(), ids.size());
    }

    private Set<Long> visibleReceivables(
            AuthenticatedCaller caller, TenantId expectedTenant, Set<Long> ids) {
        var access = receivableAccessResolver.access(caller, AuthorityCatalog.RECEIVABLES_READ,
                ReceivableAccessMode.READ);
        requireSameTenant(expectedTenant, access.tenantId());
        return receivableRepository.findVisibleIdsIn(access.tenantId(),
                        ids.stream().map(ReceivableId::new).toList(), access.visibility(), ids.size());
    }

    private Set<Long> visibleReceivablePlans(
            AuthenticatedCaller caller, TenantId expectedTenant, Set<Long> ids) {
        var access = receivableAccessResolver.access(caller, AuthorityCatalog.RECEIVABLE_PLANS_READ,
                ReceivableAccessMode.READ);
        requireSameTenant(expectedTenant, access.tenantId());
        return receivablePlanRepository.findVisibleIdsIn(access.tenantId(),
                        ids.stream().map(ReceivablePlanId::new).toList(),
                        access.visibility(), ids.size());
    }

    private Set<Long> visibleOaRequests(
            AuthenticatedCaller caller, TenantId expectedTenant, Set<Long> ids) {
        var access = oaRequestAccessResolver.access(caller);
        requireSameTenant(expectedTenant, access.tenantId());
        return oaRequestRepository.findVisibleIds(access.tenantId(),
                ids.stream().map(OaRequestId::new).toList(), access.memberId(),
                access.tenantManager());
    }

    private TenantId customer(AuthenticatedCaller caller, Long id, boolean write) {
        String authority = write ? AuthorityCatalog.CUSTOMERS_WRITE : AuthorityCatalog.CUSTOMERS_READ;
        var access = customerAccessResolver.access(caller, authority,
                write ? CustomerAccessMode.WRITE : CustomerAccessMode.READ);
        if (customerRepository.findVisibleById(access.tenantId(), new CustomerId(id),
                access.visibility()).isEmpty()) {
            throw notFound();
        }
        return access.tenantId();
    }

    private TenantId contact(AuthenticatedCaller caller, Long id, boolean write) {
        String authority = write ? AuthorityCatalog.CONTACTS_WRITE : AuthorityCatalog.CONTACTS_READ;
        var access = contactAccessResolver.access(caller, authority,
                write ? ContactAccessMode.WRITE : ContactAccessMode.READ);
        if (contactRepository.findVisibleById(access.tenantId(), new ContactId(id),
                access.visibility()).isEmpty()) {
            throw notFound();
        }
        return access.tenantId();
    }

    private TenantId lead(AuthenticatedCaller caller, Long id, boolean write) {
        String authority = write ? AuthorityCatalog.LEADS_WRITE : AuthorityCatalog.LEADS_READ;
        var access = leadAccessResolver.access(caller, authority,
                write ? LeadAccessMode.WRITE : LeadAccessMode.READ);
        if (leadRepository.findVisibleById(access.tenantId(), new LeadId(id),
                access.visibility()).isEmpty()) {
            throw notFound();
        }
        return access.tenantId();
    }

    private TenantId opportunity(AuthenticatedCaller caller, Long id, boolean write) {
        String authority = write ? AuthorityCatalog.OPPORTUNITIES_WRITE
                : AuthorityCatalog.OPPORTUNITIES_READ;
        var access = opportunityAccessResolver.access(caller, authority,
                write ? OpportunityAccessMode.WRITE : OpportunityAccessMode.READ);
        if (opportunityRepository.findVisibleById(access.tenantId(), new OpportunityId(id),
                access.visibility()).isEmpty()) {
            throw notFound();
        }
        return access.tenantId();
    }

    private TenantId contract(AuthenticatedCaller caller, Long id, boolean write) {
        String authority = write ? AuthorityCatalog.CONTRACTS_WRITE
                : AuthorityCatalog.CONTRACTS_READ;
        var access = contractAccessResolver.access(caller, authority,
                write ? ContractAccessMode.WRITE : ContractAccessMode.READ);
        if (contractRepository.findVisibleById(access.tenantId(), new ContractId(id),
                access.visibility()).isEmpty()) {
            throw notFound();
        }
        return access.tenantId();
    }

    private TenantId invoice(AuthenticatedCaller caller, Long id, boolean write) {
        String authority = write ? AuthorityCatalog.INVOICES_WRITE
                : AuthorityCatalog.INVOICES_READ;
        var access = invoiceAccessResolver.access(caller, authority,
                write ? InvoiceAccessMode.WRITE : InvoiceAccessMode.READ);
        if (invoiceRepository.findVisibleById(access.tenantId(), new InvoiceId(id),
                access.visibility()).isEmpty()) {
            throw notFound();
        }
        return access.tenantId();
    }

    private TenantId receivable(AuthenticatedCaller caller, Long id, boolean write) {
        String authority = write ? AuthorityCatalog.RECEIVABLES_WRITE
                : AuthorityCatalog.RECEIVABLES_READ;
        var access = receivableAccessResolver.access(caller, authority,
                write ? ReceivableAccessMode.WRITE : ReceivableAccessMode.READ);
        if (receivableRepository.findVisibleById(access.tenantId(), new ReceivableId(id),
                access.visibility()).isEmpty()) {
            throw notFound();
        }
        return access.tenantId();
    }

    private TenantId receivablePlan(AuthenticatedCaller caller, Long id, boolean write) {
        String authority = write ? AuthorityCatalog.RECEIVABLE_PLANS_WRITE
                : AuthorityCatalog.RECEIVABLE_PLANS_READ;
        var access = receivableAccessResolver.access(caller, authority,
                write ? ReceivableAccessMode.WRITE : ReceivableAccessMode.READ);
        if (receivablePlanRepository.findVisibleById(access.tenantId(), new ReceivablePlanId(id),
                access.visibility()).isEmpty()) {
            throw notFound();
        }
        return access.tenantId();
    }

    private TenantId oaRequest(AuthenticatedCaller caller, Long id, boolean write) {
        if (write) {
            throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "OA requests are immutable");
        }
        var access = oaRequestAccessResolver.access(caller);
        if (oaRequestRepository.findVisibleById(access.tenantId(), new OaRequestId(id),
                access.memberId(), access.tenantManager()).isEmpty()) {
            throw notFound();
        }
        return access.tenantId();
    }

    private NonRetryableApplicationException notFound() {
        return error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                "Business record was not found");
    }

    private void requireSameTenant(TenantId expected, TenantId actual) {
        if (actual == null || !expected.sameValueAs(actual)) {
            throw error(ApplicationErrorCode.ACCESS_DENIED,
                    "Business record tenant does not match");
        }
    }

    private NonRetryableApplicationException error(
            ApplicationErrorCode code, String message) {
        return new NonRetryableApplicationException(code, message);
    }
}
