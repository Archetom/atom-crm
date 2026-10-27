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
import io.github.archetom.crm.application.service.LeadConversionService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.LeadConversionVO;
import io.github.archetom.crm.domain.entity.Contact;
import io.github.archetom.crm.domain.entity.ContactAddress;
import io.github.archetom.crm.domain.entity.Customer;
import io.github.archetom.crm.domain.entity.CustomerAddress;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.Lead;
import io.github.archetom.crm.domain.entity.LeadAddress;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.exception.LeadNotFoundException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ContactAccessMode;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.LeadAccessMode;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ContactFieldCatalog;
import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
import io.github.archetom.crm.domain.policy.LeadFieldCatalog;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.LeadRepository;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.LeadId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LeadConversionServiceImpl implements LeadConversionService {

    private final LeadRepository leadRepository;
    private final CustomerRepository customerRepository;
    private final ContactRepository contactRepository;
    private final LeadAccessResolver leadAccessResolver;
    private final CustomerAccessResolver customerAccessResolver;
    private final ContactAccessResolver contactAccessResolver;
    private final LeadApplicationSupport leadSupport;
    private final CustomerApplicationSupport customerSupport;
    private final ContactApplicationSupport contactSupport;
    private final CustomizationObjectLock objectLock;
    private final CommandServiceTemplate commandTemplate;

    @Override
    public Result<LeadConversionVO> convert(
            AuthenticatedCaller caller, Long leadId, LeadConvertRequest request) {
        return commandTemplate.execute(UseCaseOperation.LEAD_CONVERT, new ServiceOperation<>() {
            private LeadAccess leadAccess;
            private CustomerAccess customerWriteAccess;
            private ContactAccess contactWriteAccess;
            private LeadId id;

            @Override
            public void validate() {
                leadAccess = leadAccessResolver.access(
                        caller, AuthorityCatalog.LEADS_CONVERT, LeadAccessMode.WRITE);
                customerWriteAccess = customerAccessResolver.access(
                        caller, AuthorityCatalog.CUSTOMERS_WRITE, CustomerAccessMode.WRITE);
                contactWriteAccess = contactAccessResolver.access(
                        caller, AuthorityCatalog.CONTACTS_WRITE, ContactAccessMode.WRITE);
                id = id(leadId);
                if (request == null) {
                    throw leadSupport.error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Lead conversion request must not be empty");
                }
                if (!leadAccess.tenantId().equals(customerWriteAccess.tenantId())
                        || !leadAccess.tenantId().equals(contactWriteAccess.tenantId())) {
                    throw leadSupport.error(ApplicationErrorCode.ACCESS_DENIED,
                            "Lead conversion tenant is invalid");
                }
            }

            @Override
            public LeadConversionVO execute() {
                objectLock.acquire(leadAccess.tenantId(), BusinessObjectType.CONTACT);
                objectLock.acquire(leadAccess.tenantId(), BusinessObjectType.CUSTOMER);
                objectLock.acquire(leadAccess.tenantId(), BusinessObjectType.LEAD);
                Lead lead = requireVisible(leadAccess, id);
                leadSupport.requireVersion(lead, request.getVersion());

                List<FieldDefinition> leadFields = leadSupport.fields(leadAccess.tenantId());
                Map<FieldCode, EffectiveFieldAccess> leadFieldAccess = leadSupport.accesses(
                        leadAccess.tenantId(), leadAccess.memberId());
                leadSupport.requireWrite(leadFieldAccess, LeadFieldCatalog.CUSTOMER);
                leadSupport.requireWrite(leadFieldAccess, LeadFieldCatalog.STATUS);
                leadSupport.requireWrite(leadFieldAccess, LeadFieldCatalog.DEAL_STATUS);
                ConversionSource source = conversionSource(lead, request, leadFieldAccess);
                OrganizationMembership owner = customerSupport.requireActiveMember(
                        leadAccess.tenantId(), source.ownerMemberId());
                requireConversionAssignment(caller, owner, customerWriteAccess, contactWriteAccess);

                List<FieldDefinition> customerFields = customerSupport.fields(leadAccess.tenantId());
                var customerFieldsByCode = customerSupport.fieldsByCode(customerFields);
                var customerFieldAccess = customerSupport.accesses(
                        leadAccess.tenantId(), customerWriteAccess.memberId());
                requireCustomerConversionFieldAccess(source, customerFieldAccess);
                Customer customer = Customer.create(leadAccess.tenantId(), source.ownerMemberId(),
                        source.customerName(), source.phoneNumber(), source.telephone(), source.email(),
                        source.website(), source.description(), customerAddresses(source.addresses()),
                        customerSupport.customFields(
                                request.getCustomerCustomFields(), List.of(), true,
                                customerFieldsByCode, customerFieldAccess));
                customer.replaceTags(source.tags());
                Customer savedCustomer = customerRepository.save(
                        leadAccess.tenantId(), customer, customerFields);

                List<FieldDefinition> contactFields = contactSupport.fields(leadAccess.tenantId());
                var contactFieldsByCode = contactSupport.fieldsByCode(contactFields);
                var contactFieldAccess = contactSupport.accesses(
                        leadAccess.tenantId(), contactWriteAccess.memberId());
                requireContactConversionFieldAccess(source, contactFieldAccess);
                Contact contact = Contact.create(leadAccess.tenantId(), source.ownerMemberId(),
                        source.contactName(), source.phoneNumber(), source.telephone(),
                        source.contactJobTitle(), source.email(), source.website(), source.description(),
                        source.nextContactTime(), contactAddresses(source.addresses()),
                        contactSupport.customFields(
                                request.getContactCustomFields(), List.of(), true,
                                contactFieldsByCode, contactFieldAccess));
                contact.replaceTags(source.tags());
                contact.changeCustomer(savedCustomer.getId());
                Contact savedContact = contactRepository.save(
                        leadAccess.tenantId(), contact, contactFields);

                lead.convert(savedCustomer.getId(), savedContact.getId());
                Lead savedLead = leadRepository.save(leadAccess.tenantId(), lead, leadFields);
                return new LeadConversionVO(savedLead.getId().getValue(),
                        savedCustomer.getId().getValue(), savedContact.getId().getValue(),
                        savedLead.getVersion());
            }
        });
    }

    private void requireConversionAssignment(
            AuthenticatedCaller caller, OrganizationMembership owner,
            CustomerAccess customerWriteAccess, ContactAccess contactWriteAccess) {
        if (owner.getMemberId().sameValueAs(customerWriteAccess.memberId())) {
            return;
        }
        CustomerAccess customerAssignAccess = customerAccessResolver.access(
                caller, AuthorityCatalog.CUSTOMERS_ASSIGN, CustomerAccessMode.OWNER_SCOPE);
        ContactAccess contactAssignAccess = contactAccessResolver.access(
                caller, AuthorityCatalog.CONTACTS_ASSIGN, ContactAccessMode.OWNER_SCOPE);
        requireAssignableOwner(owner, customerAssignAccess.visibility().dataScope(),
                customerAssignAccess.visibility().callerMemberId(),
                customerAssignAccess.visibility().ownerDepartmentIds(), "Customer");
        requireAssignableOwner(owner, contactAssignAccess.visibility().dataScope(),
                contactAssignAccess.visibility().callerMemberId(),
                contactAssignAccess.visibility().ownerDepartmentIds(), "Contact");
    }

    private void requireCustomerConversionFieldAccess(
            ConversionSource source, Map<FieldCode, EffectiveFieldAccess> accesses) {
        customerSupport.requireWrite(accesses, CustomerFieldCatalog.NAME);
        customerSupport.requireWrite(accesses, CustomerFieldCatalog.OWNER_ACTOR);
        requireCustomerWriteIfPresent(accesses, CustomerFieldCatalog.PHONE_NUMBER, source.phoneNumber());
        requireCustomerWriteIfPresent(accesses, CustomerFieldCatalog.TELEPHONE, source.telephone());
        requireCustomerWriteIfPresent(accesses, CustomerFieldCatalog.EMAIL, source.email());
        requireCustomerWriteIfPresent(accesses, CustomerFieldCatalog.WEBSITE, source.website());
        requireCustomerWriteIfPresent(accesses, CustomerFieldCatalog.DESCRIPTION, source.description());
        requireCustomerWriteIfPresent(accesses, CustomerFieldCatalog.TAGS, source.tags());
        requireCustomerWriteIfPresent(accesses, CustomerFieldCatalog.ADDRESSES, source.addresses());
    }

    private void requireContactConversionFieldAccess(
            ConversionSource source, Map<FieldCode, EffectiveFieldAccess> accesses) {
        contactSupport.requireWrite(accesses, ContactFieldCatalog.NAME);
        contactSupport.requireWrite(accesses, ContactFieldCatalog.OWNER_ACTOR);
        contactSupport.requireWrite(accesses, ContactFieldCatalog.CUSTOMER);
        requireContactWriteIfPresent(accesses, ContactFieldCatalog.PHONE_NUMBER, source.phoneNumber());
        requireContactWriteIfPresent(accesses, ContactFieldCatalog.TELEPHONE, source.telephone());
        requireContactWriteIfPresent(accesses, ContactFieldCatalog.JOB_TITLE, source.contactJobTitle());
        requireContactWriteIfPresent(accesses, ContactFieldCatalog.EMAIL, source.email());
        requireContactWriteIfPresent(accesses, ContactFieldCatalog.WEBSITE, source.website());
        requireContactWriteIfPresent(accesses, ContactFieldCatalog.DESCRIPTION, source.description());
        requireContactWriteIfPresent(accesses, ContactFieldCatalog.NEXT_CONTACT_TIME,
                source.nextContactTime());
        requireContactWriteIfPresent(accesses, ContactFieldCatalog.TAGS, source.tags());
        requireContactWriteIfPresent(accesses, ContactFieldCatalog.ADDRESSES, source.addresses());
    }

    private void requireCustomerWriteIfPresent(
            Map<FieldCode, EffectiveFieldAccess> accesses, String code, Object value) {
        if (present(value)) {
            customerSupport.requireWrite(accesses, code);
        }
    }

    private void requireContactWriteIfPresent(
            Map<FieldCode, EffectiveFieldAccess> accesses, String code, Object value) {
        if (present(value)) {
            contactSupport.requireWrite(accesses, code);
        }
    }

    private boolean present(Object value) {
        return value != null && (!(value instanceof Collection<?> values) || !values.isEmpty());
    }

    private void requireAssignableOwner(
            OrganizationMembership owner, DataScope dataScope, MemberId callerMemberId,
            Set<DepartmentId> ownerDepartmentIds, String objectName) {
        if (dataScope == DataScope.TENANT) {
            return;
        }
        if (dataScope == DataScope.SELF) {
            if (!owner.getMemberId().sameValueAs(callerMemberId)) {
                throw leadSupport.error(ApplicationErrorCode.ACCESS_DENIED,
                        objectName + " owner is outside the assignment scope");
            }
            return;
        }
        if (owner.getDepartmentId() == null
                || !ownerDepartmentIds.contains(owner.getDepartmentId())) {
            throw leadSupport.error(ApplicationErrorCode.ACCESS_DENIED,
                    objectName + " owner is outside the assignment scope");
        }
    }

    private List<CustomerAddress> customerAddresses(Collection<LeadAddress> addresses) {
        return addresses == null ? List.of() : addresses.stream().map(address ->
                new CustomerAddress(address.key(), address.label(), address.country(), address.region(),
                        address.city(), address.postalCode(), address.addressLine(), address.longitude(),
                        address.latitude(), address.primary())).toList();
    }

    private List<ContactAddress> contactAddresses(Collection<LeadAddress> addresses) {
        return addresses == null ? List.of() : addresses.stream().map(address ->
                new ContactAddress(address.key(), address.label(), address.country(), address.region(),
                        address.city(), address.postalCode(), address.addressLine(), address.longitude(),
                        address.latitude(), address.primary())).toList();
    }

    private ConversionSource conversionSource(
            Lead lead, LeadConvertRequest request,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        String leadName = request.getCustomerName() == null || request.getContactName() == null
                ? copiedLeadValue(lead.getName(), LeadFieldCatalog.NAME, accesses) : null;
        leadSupport.requireReadableUnmasked(accesses, LeadFieldCatalog.OWNER_ACTOR);
        return new ConversionSource(
                lead.getOwnerMemberId(),
                requestedName(request.getCustomerName(), leadName),
                requestedName(request.getContactName(), leadName),
                copiedLeadValue(lead.getPhoneNumber(), LeadFieldCatalog.PHONE_NUMBER, accesses),
                copiedLeadValue(lead.getTelephone(), LeadFieldCatalog.TELEPHONE, accesses),
                request.getContactJobTitle() == null
                        ? copiedLeadValue(lead.getJobTitle(), LeadFieldCatalog.JOB_TITLE, accesses)
                        : request.getContactJobTitle(),
                copiedLeadValue(lead.getEmail(), LeadFieldCatalog.EMAIL, accesses),
                copiedLeadValue(lead.getWebsite(), LeadFieldCatalog.WEBSITE, accesses),
                copiedLeadValue(lead.getDescription(), LeadFieldCatalog.DESCRIPTION, accesses),
                copiedLeadValue(lead.getNextContactTime(), LeadFieldCatalog.NEXT_CONTACT_TIME, accesses),
                copiedLeadValue(lead.getTags(), LeadFieldCatalog.TAGS, accesses),
                copiedLeadValue(lead.getAddresses(), LeadFieldCatalog.ADDRESSES, accesses));
    }

    private String requestedName(String requested, String fallback) {
        return requested == null ? fallback : requested;
    }

    private <T> T copiedLeadValue(
            T value, String code, Map<FieldCode, EffectiveFieldAccess> accesses) {
        if (value == null || value instanceof Collection<?> collection && collection.isEmpty()) {
            return value;
        }
        leadSupport.requireReadableUnmasked(accesses, code);
        return value;
    }

    private Lead requireVisible(LeadAccess access, LeadId id) {
        return leadRepository.findVisibleById(access.tenantId(), id, access.visibility())
                .orElseThrow(LeadNotFoundException::new);
    }

    private LeadId id(Long value) {
        try {
            return new LeadId(value);
        } catch (RuntimeException exception) {
            throw leadSupport.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Lead ID must be positive");
        }
    }

    private record ConversionSource(
            MemberId ownerMemberId, String customerName, String contactName,
            String phoneNumber, String telephone,
            String contactJobTitle, String email, String website, String description,
            LocalDateTime nextContactTime, List<String> tags, List<LeadAddress> addresses) {
    }
}
