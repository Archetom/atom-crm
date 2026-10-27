package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.domain.entity.Contact;
import io.github.archetom.crm.domain.entity.Contract;
import io.github.archetom.crm.domain.entity.Customer;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.Lead;
import io.github.archetom.crm.domain.entity.Invoice;
import io.github.archetom.crm.domain.entity.Opportunity;
import io.github.archetom.crm.domain.entity.OaRequest;
import io.github.archetom.crm.domain.entity.Receivable;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.policy.ContactFieldCatalog;
import io.github.archetom.crm.domain.policy.ContractFieldCatalog;
import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
import io.github.archetom.crm.domain.policy.LeadFieldCatalog;
import io.github.archetom.crm.domain.policy.InvoiceFieldCatalog;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.policy.ReceivableFieldCatalog;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.LeadRepository;
import io.github.archetom.crm.domain.repository.InvoiceRepository;
import io.github.archetom.crm.domain.repository.OpportunityRepository;
import io.github.archetom.crm.domain.repository.OaRequestRepository;
import io.github.archetom.crm.domain.repository.ReceivableRepository;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.LeadId;
import io.github.archetom.crm.domain.valueobject.InvoiceId;
import io.github.archetom.crm.domain.valueobject.OpportunityId;
import io.github.archetom.crm.domain.valueobject.OaRequestId;
import io.github.archetom.crm.domain.valueobject.ReceivableId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds approval facts from the persisted business aggregate, never from HTTP values. */
@Component
@RequiredArgsConstructor
class BusinessRecordFactResolver {

    private final CustomerRepository customerRepository;
    private final ContactRepository contactRepository;
    private final LeadRepository leadRepository;
    private final OpportunityRepository opportunityRepository;
    private final ContractRepository contractRepository;
    private final InvoiceRepository invoiceRepository;
    private final ReceivableRepository receivableRepository;
    private final OaRequestRepository oaRequestRepository;
    private final ApprovalApplicationSupport support;

    Map<FieldCode, List<String>> resolve(
            TenantId tenantId, BusinessObjectType objectType, Long subjectId,
            Collection<FieldCode> requestedCodes,
            Map<FieldCode, FieldDefinition> fields) {
        Map<FieldCode, List<String>> source = switch (objectType) {
            case CUSTOMER -> customer(customerRepository.findById(
                    tenantId, new CustomerId(subjectId)).orElseThrow(this::notFound));
            case CONTACT -> contact(contactRepository.findById(
                    tenantId, new ContactId(subjectId)).orElseThrow(this::notFound));
            case LEAD -> lead(leadRepository.findById(
                    tenantId, new LeadId(subjectId)).orElseThrow(this::notFound));
            case OPPORTUNITY -> opportunity(opportunityRepository.findById(
                    tenantId, new OpportunityId(subjectId)).orElseThrow(this::notFound));
            case CONTRACT -> contract(contractRepository.findById(
                    tenantId, new ContractId(subjectId)).orElseThrow(this::notFound));
            case INVOICE -> invoice(invoiceRepository.findById(
                    tenantId, new InvoiceId(subjectId)).orElseThrow(this::notFound));
            case RECEIVABLE -> receivable(receivableRepository.findById(
                    tenantId, new ReceivableId(subjectId)).orElseThrow(this::notFound));
            case OA_REQUEST -> oaRequest(oaRequestRepository.findById(
                    tenantId, new OaRequestId(subjectId)).orElseThrow(this::notFound));
            default -> throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Business object approval facts are unavailable");
        };
        LinkedHashMap<FieldCode, List<String>> result = new LinkedHashMap<>();
        for (FieldCode code : requestedCodes) {
            FieldDefinition field = fields.get(code);
            if (field == null || objectType != BusinessObjectType.OA_REQUEST
                    && !field.isSearchable()) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Approval routing field is unavailable");
            }
            result.put(code, objectType == BusinessObjectType.OA_REQUEST
                    ? support.normalizeSnapshotFact(
                            field, source.getOrDefault(code, List.of()))
                    : support.normalizeFact(
                            field, source.getOrDefault(code, List.of())));
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private Map<FieldCode, List<String>> oaRequest(OaRequest value) {
        return value.getFacts();
    }

    private Map<FieldCode, List<String>> customer(Customer value) {
        LinkedHashMap<FieldCode, List<String>> facts = new LinkedHashMap<>();
        put(facts, CustomerFieldCatalog.NAME, value.getName());
        put(facts, CustomerFieldCatalog.PHONE_NUMBER, value.getPhoneNumber());
        put(facts, CustomerFieldCatalog.TELEPHONE, value.getTelephone());
        put(facts, CustomerFieldCatalog.EMAIL, value.getEmail());
        put(facts, CustomerFieldCatalog.WEBSITE, value.getWebsite());
        put(facts, CustomerFieldCatalog.DESCRIPTION, value.getDescription());
        put(facts, CustomerFieldCatalog.OWNER_ACTOR, value.getOwnerMemberId());
        put(facts, CustomerFieldCatalog.PARENT_CUSTOMER, value.getParentCustomerId());
        put(facts, CustomerFieldCatalog.LOCKED, value.isLocked());
        put(facts, CustomerFieldCatalog.DEAL_STATUS, value.getDealStatus());
        put(facts, CustomerFieldCatalog.STATUS, value.getStatus());
        put(facts, CustomerFieldCatalog.CREATED_TIME, value.getCreatedTime());
        put(facts, CustomerFieldCatalog.UPDATED_TIME, value.getUpdatedTime());
        value.getCustomFieldValues().forEach(custom ->
                facts.put(custom.fieldCode(), custom.values()));
        return facts;
    }

    private Map<FieldCode, List<String>> contact(Contact value) {
        LinkedHashMap<FieldCode, List<String>> facts = new LinkedHashMap<>();
        put(facts, ContactFieldCatalog.NAME, value.getName());
        put(facts, ContactFieldCatalog.PHONE_NUMBER, value.getPhoneNumber());
        put(facts, ContactFieldCatalog.TELEPHONE, value.getTelephone());
        put(facts, ContactFieldCatalog.JOB_TITLE, value.getJobTitle());
        put(facts, ContactFieldCatalog.EMAIL, value.getEmail());
        put(facts, ContactFieldCatalog.WEBSITE, value.getWebsite());
        put(facts, ContactFieldCatalog.DESCRIPTION, value.getDescription());
        put(facts, ContactFieldCatalog.NEXT_CONTACT_TIME, value.getNextContactTime());
        put(facts, ContactFieldCatalog.OWNER_ACTOR, value.getOwnerMemberId());
        put(facts, ContactFieldCatalog.CUSTOMER, value.getCustomerId());
        put(facts, ContactFieldCatalog.PARENT_CONTACT, value.getParentContactId());
        put(facts, ContactFieldCatalog.LOCKED, value.isLocked());
        put(facts, ContactFieldCatalog.DEAL_STATUS, value.getDealStatus());
        put(facts, ContactFieldCatalog.STATUS, value.getStatus());
        put(facts, ContactFieldCatalog.CREATED_TIME, value.getCreatedTime());
        put(facts, ContactFieldCatalog.UPDATED_TIME, value.getUpdatedTime());
        value.getCustomFieldValues().forEach(custom ->
                facts.put(custom.fieldCode(), custom.values()));
        return facts;
    }

    private Map<FieldCode, List<String>> lead(Lead value) {
        LinkedHashMap<FieldCode, List<String>> facts = new LinkedHashMap<>();
        put(facts, LeadFieldCatalog.NAME, value.getName());
        put(facts, LeadFieldCatalog.PHONE_NUMBER, value.getPhoneNumber());
        put(facts, LeadFieldCatalog.TELEPHONE, value.getTelephone());
        put(facts, LeadFieldCatalog.JOB_TITLE, value.getJobTitle());
        put(facts, LeadFieldCatalog.EMAIL, value.getEmail());
        put(facts, LeadFieldCatalog.WEBSITE, value.getWebsite());
        put(facts, LeadFieldCatalog.DESCRIPTION, value.getDescription());
        put(facts, LeadFieldCatalog.NEXT_CONTACT_TIME, value.getNextContactTime());
        put(facts, LeadFieldCatalog.OWNER_ACTOR, value.getOwnerMemberId());
        put(facts, LeadFieldCatalog.CUSTOMER, value.getCustomerId());
        put(facts, LeadFieldCatalog.PARENT_LEAD, value.getParentLeadId());
        put(facts, LeadFieldCatalog.LOCKED, value.isLocked());
        put(facts, LeadFieldCatalog.DEAL_STATUS, value.getDealStatus());
        put(facts, LeadFieldCatalog.STATUS, value.getStatus());
        put(facts, LeadFieldCatalog.CREATED_TIME, value.getCreatedTime());
        put(facts, LeadFieldCatalog.UPDATED_TIME, value.getUpdatedTime());
        value.getCustomFieldValues().forEach(custom ->
                facts.put(custom.fieldCode(), custom.values()));
        return facts;
    }

    private Map<FieldCode, List<String>> opportunity(Opportunity value) {
        LinkedHashMap<FieldCode, List<String>> facts = new LinkedHashMap<>();
        put(facts, OpportunityFieldCatalog.NAME, value.getName());
        put(facts, OpportunityFieldCatalog.CUSTOMER, value.getCustomerId());
        put(facts, OpportunityFieldCatalog.PRIMARY_CONTACT, value.getPrimaryContactId());
        put(facts, OpportunityFieldCatalog.PIPELINE_CODE, value.getPipelineCode());
        put(facts, OpportunityFieldCatalog.STAGE_CODE, value.getStageCode());
        put(facts, OpportunityFieldCatalog.EXPECTED_CLOSE_DATE, value.getExpectedCloseDate());
        put(facts, OpportunityFieldCatalog.ESTIMATED_AMOUNT, value.getEstimatedAmount());
        put(facts, OpportunityFieldCatalog.NEXT_CONTACT_TIME, value.getNextContactTime());
        put(facts, OpportunityFieldCatalog.DESCRIPTION, value.getDescription());
        put(facts, OpportunityFieldCatalog.OWNER_ACTOR, value.getOwnerMemberId());
        put(facts, OpportunityFieldCatalog.OUTCOME, value.getOutcome());
        put(facts, OpportunityFieldCatalog.OUTCOME_TIME, value.getOutcomeTime());
        put(facts, OpportunityFieldCatalog.OUTCOME_REMARK, value.getOutcomeRemark());
        put(facts, OpportunityFieldCatalog.STATUS, value.getStatus());
        put(facts, OpportunityFieldCatalog.CREATED_TIME, value.getCreatedTime());
        put(facts, OpportunityFieldCatalog.UPDATED_TIME, value.getUpdatedTime());
        value.getCustomFieldValues().forEach(custom ->
                facts.put(custom.fieldCode(), custom.values()));
        return facts;
    }

    private Map<FieldCode, List<String>> contract(Contract value) {
        LinkedHashMap<FieldCode, List<String>> facts = new LinkedHashMap<>();
        put(facts, ContractFieldCatalog.NAME, value.getName());
        put(facts, ContractFieldCatalog.NUMBER, value.getNumber());
        put(facts, ContractFieldCatalog.CUSTOMER, value.getCustomerId());
        put(facts, ContractFieldCatalog.OPPORTUNITY, value.getOpportunityId());
        put(facts, ContractFieldCatalog.CONTACT, value.getContactId());
        put(facts, ContractFieldCatalog.ORDER_DATE, value.getOrderDate());
        put(facts, ContractFieldCatalog.START_DATE, value.getStartDate());
        put(facts, ContractFieldCatalog.END_DATE, value.getEndDate());
        put(facts, ContractFieldCatalog.CONTRACT_TYPE, value.getContractType());
        put(facts, ContractFieldCatalog.PAYMENT_TYPE, value.getPaymentType());
        put(facts, ContractFieldCatalog.REMARK, value.getRemark());
        put(facts, ContractFieldCatalog.SUBTOTAL, value.getSubtotal());
        put(facts, ContractFieldCatalog.DISCOUNT_RATE, value.getDiscountRate());
        put(facts, ContractFieldCatalog.TOTAL_AMOUNT, value.getTotalAmount());
        put(facts, ContractFieldCatalog.OWNER_ACTOR, value.getOwnerMemberId());
        put(facts, ContractFieldCatalog.STATUS, value.getStatus());
        put(facts, ContractFieldCatalog.CREATED_TIME, value.getCreatedTime());
        put(facts, ContractFieldCatalog.UPDATED_TIME, value.getUpdatedTime());
        value.getCustomFieldValues().forEach(custom ->
                facts.put(custom.fieldCode(), custom.values()));
        return facts;
    }

    private Map<FieldCode, List<String>> invoice(Invoice value) {
        LinkedHashMap<FieldCode, List<String>> facts = new LinkedHashMap<>();
        put(facts, InvoiceFieldCatalog.APPLICATION_NUMBER, value.getApplicationNumber());
        put(facts, InvoiceFieldCatalog.CUSTOMER, value.getCustomerId());
        put(facts, InvoiceFieldCatalog.CONTRACT, value.getContractId());
        put(facts, InvoiceFieldCatalog.AMOUNT, value.getAmount());
        put(facts, InvoiceFieldCatalog.REQUESTED_DATE, value.getRequestedDate());
        put(facts, InvoiceFieldCatalog.INVOICE_TYPE, value.getInvoiceType());
        put(facts, InvoiceFieldCatalog.TITLE_TYPE, value.getTitleType());
        put(facts, InvoiceFieldCatalog.TITLE, value.getTitle());
        put(facts, InvoiceFieldCatalog.TAX_NUMBER, value.getTaxNumber());
        put(facts, InvoiceFieldCatalog.BANK_NAME, value.getBankName());
        put(facts, InvoiceFieldCatalog.BANK_ACCOUNT, value.getBankAccount());
        put(facts, InvoiceFieldCatalog.REGISTERED_ADDRESS, value.getRegisteredAddress());
        put(facts, InvoiceFieldCatalog.REGISTERED_PHONE, value.getRegisteredPhone());
        put(facts, InvoiceFieldCatalog.CONTACT_NAME, value.getContactName());
        put(facts, InvoiceFieldCatalog.CONTACT_PHONE, value.getContactPhone());
        put(facts, InvoiceFieldCatalog.SHIPPING_ADDRESS, value.getShippingAddress());
        put(facts, InvoiceFieldCatalog.REMARK, value.getRemark());
        put(facts, InvoiceFieldCatalog.OWNER_ACTOR, value.getOwnerMemberId());
        put(facts, InvoiceFieldCatalog.STATUS, value.getStatus());
        put(facts, InvoiceFieldCatalog.INVOICE_NUMBER, value.getInvoiceNumber());
        put(facts, InvoiceFieldCatalog.ISSUED_DATE, value.getIssuedDate());
        put(facts, InvoiceFieldCatalog.LOGISTICS_NUMBER, value.getLogisticsNumber());
        put(facts, InvoiceFieldCatalog.CREATED_TIME, value.getCreatedTime());
        put(facts, InvoiceFieldCatalog.UPDATED_TIME, value.getUpdatedTime());
        value.getCustomFieldValues().forEach(custom ->
                facts.put(custom.fieldCode(), custom.values()));
        return facts;
    }

    private Map<FieldCode, List<String>> receivable(Receivable value) {
        LinkedHashMap<FieldCode, List<String>> facts = new LinkedHashMap<>();
        put(facts, ReceivableFieldCatalog.NUMBER, value.getReceiptNumber());
        put(facts, ReceivableFieldCatalog.CUSTOMER, value.getCustomerId());
        put(facts, ReceivableFieldCatalog.CONTRACT, value.getContractId());
        put(facts, ReceivableFieldCatalog.PLAN, value.getPlanId());
        put(facts, ReceivableFieldCatalog.RECEIVED_DATE, value.getReceivedDate());
        put(facts, ReceivableFieldCatalog.PAYMENT_METHOD, value.getPaymentMethod());
        put(facts, ReceivableFieldCatalog.AMOUNT, value.getAmount());
        put(facts, ReceivableFieldCatalog.REMARK, value.getRemark());
        put(facts, ReceivableFieldCatalog.OWNER_ACTOR, value.getOwnerMemberId());
        put(facts, ReceivableFieldCatalog.STATUS, value.getStatus());
        put(facts, ReceivableFieldCatalog.CREATED_TIME, value.getCreatedTime());
        put(facts, ReceivableFieldCatalog.UPDATED_TIME, value.getUpdatedTime());
        value.getCustomFieldValues().forEach(custom ->
                facts.put(custom.fieldCode(), custom.values()));
        return facts;
    }

    private void put(Map<FieldCode, List<String>> facts, String code, Object value) {
        if (value == null) {
            return;
        }
        String text = value instanceof Enum<?> enumValue ? enumValue.name()
                : value instanceof io.github.archetom.crm.domain.valueobject.MemberId memberId
                ? memberId.getValue().toString()
                : value instanceof io.github.archetom.crm.domain.valueobject.CustomerId customerId
                ? customerId.getValue().toString()
                : value instanceof io.github.archetom.crm.domain.valueobject.ContactId contactId
                ? contactId.getValue().toString()
                : value instanceof io.github.archetom.crm.domain.valueobject.LeadId leadId
                ? leadId.getValue().toString()
                : value instanceof io.github.archetom.crm.domain.valueobject.OpportunityId opportunityId
                ? opportunityId.getValue().toString()
                : value instanceof io.github.archetom.crm.domain.valueobject.ContractId contractId
                ? contractId.getValue().toString()
                : value instanceof io.github.archetom.crm.domain.valueobject.ReceivablePlanId planId
                ? planId.getValue().toString()
                : value instanceof java.math.BigDecimal decimal ? decimal.toPlainString()
                : value.toString();
        facts.put(new FieldCode(code), List.of(text));
    }

    private RuntimeException notFound() {
        return support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                "Business record was not found");
    }
}
