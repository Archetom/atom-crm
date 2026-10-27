package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
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
import io.github.archetom.crm.domain.model.ContactAccessMode;
import io.github.archetom.crm.domain.model.ContactStatus;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.ContractStatus;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.CustomerStatus;
import io.github.archetom.crm.domain.model.InvoiceAccessMode;
import io.github.archetom.crm.domain.model.InvoiceStatus;
import io.github.archetom.crm.domain.model.LeadAccessMode;
import io.github.archetom.crm.domain.model.LeadStatus;
import io.github.archetom.crm.domain.model.OpportunityAccessMode;
import io.github.archetom.crm.domain.model.OpportunityStatus;
import io.github.archetom.crm.domain.model.ReceivableAccessMode;
import io.github.archetom.crm.domain.model.ReceivablePlanStatus;
import io.github.archetom.crm.domain.model.ReceivableStatus;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
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
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Typed B24 bridge to the eight CRM roots. It intentionally does not use BusinessRecordAccessGuard:
 * attachment collaboration has different lifecycle and parent-lock semantics.
 */
@Component
@RequiredArgsConstructor
public class CrmAttachmentTargetAccessSupport {
    private final OrganizationTenantRepository tenantRepository;
    private final CustomerAccessResolver customerAccessResolver;
    private final CustomerRepository customerRepository;
    private final ContactAccessResolver contactAccessResolver;
    private final ContactRepository contactRepository;
    private final LeadAccessResolver leadAccessResolver;
    private final LeadRepository leadRepository;
    private final OpportunityAccessResolver opportunityAccessResolver;
    private final OpportunityRepository opportunityRepository;
    private final ContractAccessResolver contractAccessResolver;
    private final ContractRepository contractRepository;
    private final InvoiceAccessResolver invoiceAccessResolver;
    private final InvoiceRepository invoiceRepository;
    private final ReceivableAccessResolver receivableAccessResolver;
    private final ReceivableRepository receivableRepository;
    private final ReceivablePlanRepository receivablePlanRepository;

    public TenantId authorizeRead(AuthenticatedCaller caller, AttachmentTarget target) {
        return authorize(caller, target, false, false).tenantId();
    }

    public TenantId authorizeCollaborate(AuthenticatedCaller caller, AttachmentTarget target) {
        return authorize(caller, target, true, false).tenantId();
    }

    public AttachmentTargetLifecycle lockForAttachmentMutation(
            AuthenticatedCaller caller, AttachmentTarget target, AttachmentTargetOperation operation) {
        boolean collaborate = switch (operation) {
            case CREATE_INTENT, UPLOAD, STATUS, WITHDRAW -> true;
            case DOWNLOAD -> false;
            case PUBLISH, PARENT_LIFECYCLE -> throw new IllegalArgumentException(
                    "worker attachment target lock requires its dedicated internal coordinator");
        };
        return authorize(caller, target, collaborate, true);
    }

    public AttachmentTargetLifecycle lockForWorker(TenantId tenantId, AttachmentTarget target) {
        requireCrmTarget(target);
        if (tenantId == null || !tenantRepository.lockForTenantChanges(tenantId)) {
            throw new IllegalStateException("attachment target tenant is unavailable");
        }
        return switch (target.type()) {
            case CUSTOMER -> customerRepository.findByIdForAttachmentMutation(tenantId,
                    new CustomerId(target.id())).map(value -> workerLifecycle(tenantId, value))
                    .orElseThrow(this::targetMissing);
            case CONTACT -> contactRepository.findByIdForAttachmentMutation(tenantId,
                    new ContactId(target.id())).map(value -> workerLifecycle(tenantId, value))
                    .orElseThrow(this::targetMissing);
            case LEAD -> leadRepository.findByIdForAttachmentMutation(tenantId,
                    new LeadId(target.id())).map(value -> workerLifecycle(tenantId, attachmentsPermitted(value)))
                    .orElseThrow(this::targetMissing);
            case OPPORTUNITY -> opportunityRepository.findByIdForAttachmentMutation(tenantId,
                    new OpportunityId(target.id())).map(value -> workerLifecycle(tenantId, attachmentsPermitted(value)))
                    .orElseThrow(this::targetMissing);
            case CONTRACT -> contractRepository.findByIdForAttachmentMutation(tenantId,
                    new ContractId(target.id())).map(value -> workerLifecycle(tenantId, attachmentsPermitted(value)))
                    .orElseThrow(this::targetMissing);
            case INVOICE -> invoiceRepository.findByIdForAttachmentMutation(tenantId,
                    new InvoiceId(target.id())).map(value -> workerLifecycle(tenantId, attachmentsPermitted(value)))
                    .orElseThrow(this::targetMissing);
            case RECEIVABLE -> receivableRepository.findByIdForAttachmentMutation(tenantId,
                    new ReceivableId(target.id())).map(value -> workerLifecycle(tenantId, attachmentsPermitted(value)))
                    .orElseThrow(this::targetMissing);
            case RECEIVABLE_PLAN -> receivablePlanRepository.findByIdForAttachmentMutation(tenantId,
                    new ReceivablePlanId(target.id())).map(value -> workerLifecycle(tenantId, attachmentsPermitted(value)))
                    .orElseThrow(this::targetMissing);
            case WORK_TASK, PRODUCT, RETURN_VISIT, MARKETING_ACTIVITY, OA_ANNOUNCEMENT, OA_REQUEST, IMPORT_DRAFT ->
                    throw new IllegalArgumentException("CRM attachment target is required");
        };
    }

    private AttachmentTargetLifecycle authorize(
            AuthenticatedCaller caller, AttachmentTarget target, boolean collaborate, boolean forUpdate) {
        requireCrmTarget(target);
        return switch (target.type()) {
            case CUSTOMER -> customer(caller, target, collaborate, forUpdate);
            case CONTACT -> contact(caller, target, collaborate, forUpdate);
            case LEAD -> lead(caller, target, collaborate, forUpdate);
            case OPPORTUNITY -> opportunity(caller, target, collaborate, forUpdate);
            case CONTRACT -> contract(caller, target, collaborate, forUpdate);
            case INVOICE -> invoice(caller, target, collaborate, forUpdate);
            case RECEIVABLE -> receivable(caller, target, collaborate, forUpdate);
            case RECEIVABLE_PLAN -> receivablePlan(caller, target, collaborate, forUpdate);
            case WORK_TASK, PRODUCT, RETURN_VISIT, MARKETING_ACTIVITY, OA_ANNOUNCEMENT, OA_REQUEST, IMPORT_DRAFT ->
                    throw new IllegalArgumentException("CRM attachment target is required");
        };
    }

    private AttachmentTargetLifecycle customer(
            AuthenticatedCaller caller, AttachmentTarget target, boolean collaborate, boolean forUpdate) {
        var access = customerAccessResolver.access(caller,
                collaborate ? AuthorityCatalog.CUSTOMERS_WRITE : AuthorityCatalog.CUSTOMERS_READ,
                collaborate ? CustomerAccessMode.WRITE : CustomerAccessMode.READ);
        Customer value = (forUpdate ? customerRepository.findVisibleByIdForUpdate(access.tenantId(),
                new CustomerId(target.id()), access.visibility()) : customerRepository.findVisibleById(access.tenantId(),
                new CustomerId(target.id()), access.visibility())).orElseThrow(this::targetMissing);
        return lifecycle(access.tenantId(), attachmentsPermitted(value));
    }

    private AttachmentTargetLifecycle contact(
            AuthenticatedCaller caller, AttachmentTarget target, boolean collaborate, boolean forUpdate) {
        var access = contactAccessResolver.access(caller,
                collaborate ? AuthorityCatalog.CONTACTS_WRITE : AuthorityCatalog.CONTACTS_READ,
                collaborate ? ContactAccessMode.WRITE : ContactAccessMode.READ);
        Contact value = (forUpdate ? contactRepository.findVisibleByIdForUpdate(access.tenantId(),
                new ContactId(target.id()), access.visibility()) : contactRepository.findVisibleById(access.tenantId(),
                new ContactId(target.id()), access.visibility())).orElseThrow(this::targetMissing);
        return lifecycle(access.tenantId(), attachmentsPermitted(value));
    }

    private AttachmentTargetLifecycle lead(
            AuthenticatedCaller caller, AttachmentTarget target, boolean collaborate, boolean forUpdate) {
        var access = leadAccessResolver.access(caller,
                collaborate ? AuthorityCatalog.LEADS_WRITE : AuthorityCatalog.LEADS_READ,
                collaborate ? LeadAccessMode.WRITE : LeadAccessMode.READ);
        Lead value = (forUpdate ? leadRepository.findVisibleByIdForUpdate(access.tenantId(),
                new LeadId(target.id()), access.visibility()) : leadRepository.findVisibleById(access.tenantId(),
                new LeadId(target.id()), access.visibility())).orElseThrow(this::targetMissing);
        return lifecycle(access.tenantId(), attachmentsPermitted(value));
    }

    private AttachmentTargetLifecycle opportunity(
            AuthenticatedCaller caller, AttachmentTarget target, boolean collaborate, boolean forUpdate) {
        var access = opportunityAccessResolver.access(caller,
                collaborate ? AuthorityCatalog.OPPORTUNITIES_WRITE : AuthorityCatalog.OPPORTUNITIES_READ,
                collaborate ? OpportunityAccessMode.WRITE : OpportunityAccessMode.READ);
        Opportunity value = (forUpdate ? opportunityRepository.findVisibleByIdForUpdate(access.tenantId(),
                new OpportunityId(target.id()), access.visibility()) : opportunityRepository.findVisibleById(access.tenantId(),
                new OpportunityId(target.id()), access.visibility())).orElseThrow(this::targetMissing);
        return lifecycle(access.tenantId(), attachmentsPermitted(value));
    }

    private AttachmentTargetLifecycle contract(
            AuthenticatedCaller caller, AttachmentTarget target, boolean collaborate, boolean forUpdate) {
        var access = contractAccessResolver.access(caller,
                collaborate ? AuthorityCatalog.CONTRACTS_WRITE : AuthorityCatalog.CONTRACTS_READ,
                collaborate ? ContractAccessMode.WRITE : ContractAccessMode.READ);
        Contract value = (forUpdate ? contractRepository.findVisibleByIdForUpdate(access.tenantId(),
                new ContractId(target.id()), access.visibility()) : contractRepository.findVisibleById(access.tenantId(),
                new ContractId(target.id()), access.visibility())).orElseThrow(this::targetMissing);
        return lifecycle(access.tenantId(), attachmentsPermitted(value));
    }

    private AttachmentTargetLifecycle invoice(
            AuthenticatedCaller caller, AttachmentTarget target, boolean collaborate, boolean forUpdate) {
        var access = invoiceAccessResolver.access(caller,
                collaborate ? AuthorityCatalog.INVOICES_WRITE : AuthorityCatalog.INVOICES_READ,
                collaborate ? InvoiceAccessMode.WRITE : InvoiceAccessMode.READ);
        Invoice value = (forUpdate ? invoiceRepository.findVisibleByIdForUpdate(access.tenantId(),
                new InvoiceId(target.id()), access.visibility()) : invoiceRepository.findVisibleById(access.tenantId(),
                new InvoiceId(target.id()), access.visibility())).orElseThrow(this::targetMissing);
        return lifecycle(access.tenantId(), attachmentsPermitted(value));
    }

    private AttachmentTargetLifecycle receivable(
            AuthenticatedCaller caller, AttachmentTarget target, boolean collaborate, boolean forUpdate) {
        var access = receivableAccessResolver.access(caller,
                collaborate ? AuthorityCatalog.RECEIVABLES_WRITE : AuthorityCatalog.RECEIVABLES_READ,
                collaborate ? ReceivableAccessMode.WRITE : ReceivableAccessMode.READ);
        Receivable value = (forUpdate ? receivableRepository.findVisibleByIdForUpdate(access.tenantId(),
                new ReceivableId(target.id()), access.visibility()) : receivableRepository.findVisibleById(access.tenantId(),
                new ReceivableId(target.id()), access.visibility())).orElseThrow(this::targetMissing);
        return lifecycle(access.tenantId(), attachmentsPermitted(value));
    }

    private AttachmentTargetLifecycle receivablePlan(
            AuthenticatedCaller caller, AttachmentTarget target, boolean collaborate, boolean forUpdate) {
        var access = receivableAccessResolver.access(caller,
                collaborate ? AuthorityCatalog.RECEIVABLE_PLANS_WRITE : AuthorityCatalog.RECEIVABLE_PLANS_READ,
                collaborate ? ReceivableAccessMode.WRITE : ReceivableAccessMode.READ);
        ReceivablePlan value = (forUpdate ? receivablePlanRepository.findVisibleByIdForUpdate(access.tenantId(),
                new ReceivablePlanId(target.id()), access.visibility()) : receivablePlanRepository.findVisibleById(access.tenantId(),
                new ReceivablePlanId(target.id()), access.visibility())).orElseThrow(this::targetMissing);
        return lifecycle(access.tenantId(), attachmentsPermitted(value));
    }

    private AttachmentTargetLifecycle lifecycle(TenantId tenantId, boolean permitted) {
        if (!permitted) throw targetMissing();
        return new AttachmentTargetLifecycle(tenantId, true);
    }

    private boolean attachmentsPermitted(Customer value) { return value.getStatus() == CustomerStatus.ACTIVE; }
    private boolean attachmentsPermitted(Contact value) { return value.getStatus() == ContactStatus.ACTIVE; }
    private AttachmentTargetLifecycle workerLifecycle(TenantId tenantId, Customer value) {
        return new AttachmentTargetLifecycle(tenantId, attachmentsPermitted(value),
                value.getStatus() == CustomerStatus.IN_POOL);
    }
    private AttachmentTargetLifecycle workerLifecycle(TenantId tenantId, Contact value) {
        return new AttachmentTargetLifecycle(tenantId, attachmentsPermitted(value),
                value.getStatus() == ContactStatus.IN_POOL);
    }
    private AttachmentTargetLifecycle workerLifecycle(TenantId tenantId, boolean permitted) {
        return new AttachmentTargetLifecycle(tenantId, permitted);
    }
    private boolean attachmentsPermitted(Lead value) {
        return value.getStatus() == LeadStatus.ACTIVE || value.getStatus() == LeadStatus.CONVERTED;
    }
    private boolean attachmentsPermitted(Opportunity value) { return value.getStatus() == OpportunityStatus.ACTIVE; }
    private boolean attachmentsPermitted(Contract value) {
        return value.getStatus() == ContractStatus.ACTIVE || value.getStatus() == ContractStatus.VOIDED;
    }
    private boolean attachmentsPermitted(Invoice value) {
        return value.getStatus() == InvoiceStatus.PENDING || value.getStatus() == InvoiceStatus.ISSUED;
    }
    private boolean attachmentsPermitted(Receivable value) { return value.getStatus() == ReceivableStatus.ACTIVE; }
    private boolean attachmentsPermitted(ReceivablePlan value) {
        return value.getStatus() == ReceivablePlanStatus.ACTIVE;
    }

    private static void requireCrmTarget(AttachmentTarget target) {
        if (target == null || target.type() == null || target.type() == AttachmentTargetType.WORK_TASK) {
            throw new IllegalArgumentException("CRM attachment target is required");
        }
    }

    private NonRetryableApplicationException targetMissing() {
        return new NonRetryableApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                "Requested resource was not found");
    }
}
