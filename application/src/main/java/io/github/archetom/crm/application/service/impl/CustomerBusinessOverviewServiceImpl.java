package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.ContactAccessResolver;
import io.github.archetom.crm.application.security.ContractAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.InvoiceAccessResolver;
import io.github.archetom.crm.application.security.OpportunityAccessResolver;
import io.github.archetom.crm.application.security.ReceivableAccessResolver;
import io.github.archetom.crm.application.security.ReturnVisitAccessResolver;
import io.github.archetom.crm.application.service.CustomerBusinessOverviewService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.CustomerBusinessOverviewVO;
import io.github.archetom.crm.domain.exception.CustomerNotFoundException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ContactAccessMode;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.InvoiceAccessMode;
import io.github.archetom.crm.domain.model.OpportunityAccessMode;
import io.github.archetom.crm.domain.model.ReceivableAccessMode;
import io.github.archetom.crm.domain.model.ReturnVisitAccessMode;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ContactFieldCatalog;
import io.github.archetom.crm.domain.policy.ContractFieldCatalog;
import io.github.archetom.crm.domain.policy.InvoiceFieldCatalog;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.policy.ReceivableFieldCatalog;
import io.github.archetom.crm.domain.policy.ReceivablePlanFieldCatalog;
import io.github.archetom.crm.domain.policy.ReturnVisitFieldCatalog;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.InvoiceRepository;
import io.github.archetom.crm.domain.repository.OpportunityRepository;
import io.github.archetom.crm.domain.repository.ReceivablePlanRepository;
import io.github.archetom.crm.domain.repository.ReceivableRepository;
import io.github.archetom.crm.domain.repository.ReturnVisitRepository;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Builds the seven fixed customer cards in one read-only repeatable-read snapshot. */
@Service
@RequiredArgsConstructor
public class CustomerBusinessOverviewServiceImpl implements CustomerBusinessOverviewService {

    private static final String AVAILABLE = "AVAILABLE";
    private static final String UNAVAILABLE = "UNAVAILABLE";

    private final CustomerAccessResolver customerAccessResolver;
    private final ContactAccessResolver contactAccessResolver;
    private final OpportunityAccessResolver opportunityAccessResolver;
    private final ContractAccessResolver contractAccessResolver;
    private final InvoiceAccessResolver invoiceAccessResolver;
    private final ReceivableAccessResolver receivableAccessResolver;
    private final ReturnVisitAccessResolver returnVisitAccessResolver;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final CustomerRepository customerRepository;
    private final ContactRepository contactRepository;
    private final OpportunityRepository opportunityRepository;
    private final ContractRepository contractRepository;
    private final InvoiceRepository invoiceRepository;
    private final ReceivableRepository receivableRepository;
    private final ReceivablePlanRepository receivablePlanRepository;
    private final ReturnVisitRepository returnVisitRepository;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<CustomerBusinessOverviewVO> get(AuthenticatedCaller caller, Long customerId) {
        return queryTemplate.execute(UseCaseOperation.CUSTOMER_BUSINESS_OVERVIEW_GET,
                new ServiceOperation<>() {
                    private CustomerAccessResolver.CustomerAccess customerAccess;
                    private CustomerId id;

                    @Override
                    public void validate() {
                        id = customerId(customerId);
                    }

                    @Override
                    public CustomerBusinessOverviewVO execute() {
                        customerAccess = customerAccessResolver.access(caller,
                                AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
                        customerRepository.findVisibleById(customerAccess.tenantId(), id,
                                        customerAccess.visibility())
                                .orElseThrow(CustomerNotFoundException::new);
                        TenantId tenantId = customerAccess.tenantId();
                        return new CustomerBusinessOverviewVO(id.getValue(), List.of(
                                contacts(caller, tenantId, id),
                                opportunities(caller, tenantId, id),
                                contracts(caller, tenantId, id),
                                invoices(caller, tenantId, id),
                                receivables(caller, tenantId, id),
                                receivablePlans(caller, tenantId, id),
                                returnVisits(caller, tenantId, id)));
                    }
                });
    }

    private CustomerBusinessOverviewVO.CardVO contacts(
            AuthenticatedCaller caller, TenantId tenantId, CustomerId customerId) {
        try {
            ContactAccessResolver.ContactAccess access = contactAccessResolver.access(caller,
                    AuthorityCatalog.CONTACTS_READ, ContactAccessMode.READ);
            requireSameTenant(tenantId, access.tenantId());
            requireFields(access.tenantId(), access.memberId(), BusinessObjectType.CONTACT,
                    ContactFieldCatalog.CUSTOMER, ContactFieldCatalog.STATUS);
            long count = contactRepository.summarizeByCustomer(tenantId, customerId,
                    access.visibility()).count();
            return available("CONTACT", count, null);
        } catch (NonRetryableApplicationException exception) {
            return unavailableOrRethrow("CONTACT", exception);
        }
    }

    private CustomerBusinessOverviewVO.CardVO opportunities(
            AuthenticatedCaller caller, TenantId tenantId, CustomerId customerId) {
        try {
            OpportunityAccessResolver.OpportunityAccess access = opportunityAccessResolver.access(caller,
                    AuthorityCatalog.OPPORTUNITIES_READ, OpportunityAccessMode.READ);
            requireSameTenant(tenantId, access.tenantId());
            requireFields(access.tenantId(), access.memberId(), BusinessObjectType.OPPORTUNITY,
                    OpportunityFieldCatalog.CUSTOMER, OpportunityFieldCatalog.STATUS,
                    OpportunityFieldCatalog.OUTCOME, OpportunityFieldCatalog.ESTIMATED_AMOUNT);
            var summary = opportunityRepository.summarizeByCustomer(tenantId, customerId,
                    access.visibility());
            return available("OPEN_OPPORTUNITY", summary.count(), summary.estimatedAmount());
        } catch (NonRetryableApplicationException exception) {
            return unavailableOrRethrow("OPEN_OPPORTUNITY", exception);
        }
    }

    private CustomerBusinessOverviewVO.CardVO contracts(
            AuthenticatedCaller caller, TenantId tenantId, CustomerId customerId) {
        try {
            ContractAccessResolver.ContractAccess access = contractAccessResolver.access(caller,
                    AuthorityCatalog.CONTRACTS_READ, ContractAccessMode.READ);
            requireSameTenant(tenantId, access.tenantId());
            requireFields(access.tenantId(), access.memberId(), BusinessObjectType.CONTRACT,
                    ContractFieldCatalog.CUSTOMER, ContractFieldCatalog.STATUS,
                    ContractFieldCatalog.TOTAL_AMOUNT);
            var summary = contractRepository.summarizeByCustomer(tenantId, customerId,
                    access.visibility());
            return available("ACTIVE_CONTRACT", summary.count(), summary.totalAmount());
        } catch (NonRetryableApplicationException exception) {
            return unavailableOrRethrow("ACTIVE_CONTRACT", exception);
        }
    }

    private CustomerBusinessOverviewVO.CardVO invoices(
            AuthenticatedCaller caller, TenantId tenantId, CustomerId customerId) {
        try {
            InvoiceAccessResolver.InvoiceAccess access = invoiceAccessResolver.access(caller,
                    AuthorityCatalog.INVOICES_READ, InvoiceAccessMode.READ);
            requireSameTenant(tenantId, access.tenantId());
            requireFields(access.tenantId(), access.memberId(), BusinessObjectType.INVOICE,
                    InvoiceFieldCatalog.CUSTOMER, InvoiceFieldCatalog.STATUS,
                    InvoiceFieldCatalog.AMOUNT);
            var summary = invoiceRepository.summarizeByCustomer(tenantId, customerId,
                    access.visibility());
            return available("PENDING_INVOICE", summary.count(), summary.amount());
        } catch (NonRetryableApplicationException exception) {
            return unavailableOrRethrow("PENDING_INVOICE", exception);
        }
    }

    private CustomerBusinessOverviewVO.CardVO receivables(
            AuthenticatedCaller caller, TenantId tenantId, CustomerId customerId) {
        try {
            ReceivableAccessResolver.ReceivableAccess access = receivableAccessResolver.access(caller,
                    AuthorityCatalog.RECEIVABLES_READ, ReceivableAccessMode.READ);
            requireSameTenant(tenantId, access.tenantId());
            requireFields(access.tenantId(), access.memberId(), BusinessObjectType.RECEIVABLE,
                    ReceivableFieldCatalog.CUSTOMER, ReceivableFieldCatalog.STATUS,
                    ReceivableFieldCatalog.AMOUNT);
            var summary = receivableRepository.summarizeByCustomer(tenantId, customerId,
                    access.visibility());
            return available("ACTIVE_RECEIVABLE", summary.count(), summary.amount());
        } catch (NonRetryableApplicationException exception) {
            return unavailableOrRethrow("ACTIVE_RECEIVABLE", exception);
        }
    }

    private CustomerBusinessOverviewVO.CardVO receivablePlans(
            AuthenticatedCaller caller, TenantId tenantId, CustomerId customerId) {
        try {
            ReceivableAccessResolver.ReceivableAccess access = receivableAccessResolver.access(caller,
                    AuthorityCatalog.RECEIVABLE_PLANS_READ, ReceivableAccessMode.READ);
            requireSameTenant(tenantId, access.tenantId());
            requireFields(access.tenantId(), access.memberId(), BusinessObjectType.RECEIVABLE_PLAN,
                    ReceivablePlanFieldCatalog.CUSTOMER, ReceivablePlanFieldCatalog.STATUS,
                    ReceivablePlanFieldCatalog.PLANNED_AMOUNT);
            var summary = receivablePlanRepository.summarizeByCustomer(tenantId, customerId,
                    access.visibility());
            return available("ACTIVE_RECEIVABLE_PLAN", summary.count(), summary.plannedAmount());
        } catch (NonRetryableApplicationException exception) {
            return unavailableOrRethrow("ACTIVE_RECEIVABLE_PLAN", exception);
        }
    }

    private CustomerBusinessOverviewVO.CardVO returnVisits(
            AuthenticatedCaller caller, TenantId tenantId, CustomerId customerId) {
        try {
            ReturnVisitAccessResolver.ReturnVisitAccess access = returnVisitAccessResolver.access(caller,
                    AuthorityCatalog.RETURN_VISITS_READ, ReturnVisitAccessMode.READ);
            requireSameTenant(tenantId, access.tenantId());
            requireFields(access.tenantId(), access.memberId(), BusinessObjectType.RETURN_VISIT,
                    ReturnVisitFieldCatalog.CUSTOMER, ReturnVisitFieldCatalog.STATUS);
            long count = returnVisitRepository.summarizeByCustomer(tenantId, customerId,
                    access.visibility()).count();
            return available("RETURN_VISIT", count, null);
        } catch (NonRetryableApplicationException exception) {
            return unavailableOrRethrow("RETURN_VISIT", exception);
        }
    }

    private void requireFields(
            TenantId tenantId, MemberId memberId, BusinessObjectType objectType, String... fieldCodes) {
        Map<FieldCode, EffectiveFieldAccess> fields = fieldAccessResolver.resolve(
                tenantId, memberId, objectType);
        for (String fieldCode : fieldCodes) {
            EffectiveFieldAccess access = fields.get(new FieldCode(fieldCode));
            if (access == null || access.accessLevel() == FieldAccessLevel.HIDDEN
                    || access.maskType() != FieldMaskType.NONE) {
                throw new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED,
                        "Overview source field is not readable");
            }
        }
    }

    private void requireSameTenant(TenantId expected, TenantId actual) {
        if (expected == null || actual == null || !expected.sameValueAs(actual)) {
            throw new IllegalStateException("overview source tenant is inconsistent");
        }
    }

    private CustomerBusinessOverviewVO.CardVO available(
            String source, long count, BigDecimal amount) {
        return new CustomerBusinessOverviewVO.CardVO(source, AVAILABLE, Math.max(0L, count),
                amount == null ? null : amount.setScale(2));
    }

    private CustomerBusinessOverviewVO.CardVO unavailableOrRethrow(
            String source, NonRetryableApplicationException exception) {
        if (exception.getErrorCode() != ApplicationErrorCode.ACCESS_DENIED) {
            throw exception;
        }
        return new CustomerBusinessOverviewVO.CardVO(source, UNAVAILABLE, null, null);
    }

    private CustomerId customerId(Long value) {
        try {
            return new CustomerId(value);
        } catch (RuntimeException exception) {
            throw new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID,
                    "Customer ID must be positive");
        }
    }
}
