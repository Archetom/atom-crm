package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.Customer;
import io.github.archetom.crm.domain.entity.CustomerAddress;
import io.github.archetom.crm.domain.entity.CustomerCollaborator;
import io.github.archetom.crm.domain.entity.CustomerFieldValue;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.model.CustomerCollaboratorAccess;
import io.github.archetom.crm.domain.model.CustomerDealStatus;
import io.github.archetom.crm.domain.model.CustomerStatus;
import io.github.archetom.crm.domain.policy.CustomerTagPolicy;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerAddressPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerCollaboratorPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerTagPO;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class CustomerPOConverter {

    private final ObjectMapper objectMapper;

    public CustomerPOConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CustomerPO toPO(Customer customer) {
        if (customer == null) {
            return null;
        }
        CustomerPO po = new CustomerPO();
        po.setId(customer.getId() == null ? null : customer.getId().getValue());
        po.setTenantId(customer.getTenantId().getValue());
        po.setOwnerActorId(customer.getOwnerMemberId() == null
                ? null : customer.getOwnerMemberId().getValue());
        po.setParentCustomerId(customer.getParentCustomerId() == null
                ? null : customer.getParentCustomerId().getValue());
        po.setName(customer.getName());
        po.setPhoneNumber(customer.getPhoneNumber());
        po.setTelephone(customer.getTelephone());
        po.setEmail(customer.getEmail());
        po.setWebsite(customer.getWebsite());
        po.setDescription(customer.getDescription());
        po.setStatus(customer.getStatus().name());
        po.setLocked(customer.isLocked());
        po.setDealStatus(customer.getDealStatus().name());
        po.setDealTime(customer.getDealTime());
        po.setVersion(customer.getVersion());
        po.setCreatedTime(customer.getCreatedTime());
        po.setUpdatedTime(customer.getUpdatedTime());
        return po;
    }

    public Customer toDomain(
            CustomerPO po,
            List<CustomerAddressPO> addresses,
            List<CustomerTagPO> tags,
            List<CustomerCollaboratorPO> collaborators,
            List<CustomerFieldValuePO> fieldValues,
            Map<Long, FieldDefinition> fieldsById) {
        if (po == null) {
            return null;
        }
        return Customer.reconstitute(new Customer.Snapshot(
                new CustomerId(po.getId()), new TenantId(po.getTenantId()),
                po.getOwnerActorId() == null ? null : new MemberId(po.getOwnerActorId()),
                po.getParentCustomerId() == null ? null : new CustomerId(po.getParentCustomerId()),
                po.getName(), po.getPhoneNumber(), po.getTelephone(), po.getEmail(), po.getWebsite(),
                po.getDescription(), CustomerStatus.valueOf(po.getStatus()),
                Boolean.TRUE.equals(po.getLocked()), CustomerDealStatus.valueOf(po.getDealStatus()),
                po.getDealTime(), tags.stream().map(CustomerTagPO::getTagName).toList(),
                addresses.stream().map(this::toAddress).toList(),
                collaborators.stream().map(value -> new CustomerCollaborator(
                        new MemberId(value.getActorId()),
                        CustomerCollaboratorAccess.valueOf(value.getAccessLevel()))).toList(),
                fieldValues.stream().map(value -> toFieldValue(value, fieldsById)).toList(),
                po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
    }

    public CustomerAddressPO toPO(
            TenantId tenantId, CustomerId customerId, CustomerAddress address) {
        CustomerAddressPO po = new CustomerAddressPO();
        po.setTenantId(tenantId.getValue());
        po.setCustomerId(customerId.getValue());
        po.setAddressKey(address.key());
        po.setLabel(address.label());
        po.setCountry(address.country());
        po.setRegion(address.region());
        po.setCity(address.city());
        po.setPostalCode(address.postalCode());
        po.setAddressLine(address.addressLine());
        po.setLongitude(address.longitude());
        po.setLatitude(address.latitude());
        po.setPrimaryAddress(address.primary());
        LocalDateTime now = LocalDateTime.now();
        po.setCreatedTime(now);
        po.setUpdatedTime(now);
        return po;
    }

    public CustomerTagPO toPO(TenantId tenantId, CustomerId customerId, String tag) {
        CustomerTagPO po = new CustomerTagPO();
        po.setTenantId(tenantId.getValue());
        po.setCustomerId(customerId.getValue());
        po.setTagName(tag);
        po.setTagKey(CustomerTagPolicy.key(tag));
        po.setCreatedTime(LocalDateTime.now());
        return po;
    }

    public CustomerCollaboratorPO toPO(
            TenantId tenantId, CustomerId customerId, CustomerCollaborator collaborator) {
        CustomerCollaboratorPO po = new CustomerCollaboratorPO();
        po.setTenantId(tenantId.getValue());
        po.setCustomerId(customerId.getValue());
        po.setActorId(collaborator.memberId().getValue());
        po.setAccessLevel(collaborator.access().name());
        LocalDateTime now = LocalDateTime.now();
        po.setCreatedTime(now);
        po.setUpdatedTime(now);
        return po;
    }

    public CustomerFieldValuePO toPO(
            TenantId tenantId, CustomerId customerId,
            CustomerFieldValue value, FieldDefinition field) {
        try {
            CustomerFieldValuePO po = new CustomerFieldValuePO();
            po.setTenantId(tenantId.getValue());
            po.setCustomerId(customerId.getValue());
            po.setObjectType(field.getObjectType().name());
            po.setFieldId(field.getId().getValue());
            po.setValuesJson(objectMapper.writeValueAsString(value.values()));
            po.setScalarValue(value.values().size() == 1 ? value.values().getFirst() : null);
            LocalDateTime now = LocalDateTime.now();
            po.setCreatedTime(now);
            po.setUpdatedTime(now);
            return po;
        } catch (Exception exception) {
            throw new IllegalStateException("Customer field value cannot be serialized", exception);
        }
    }

    private CustomerAddress toAddress(CustomerAddressPO po) {
        return new CustomerAddress(po.getAddressKey(), po.getLabel(), po.getCountry(),
                po.getRegion(), po.getCity(), po.getPostalCode(), po.getAddressLine(),
                po.getLongitude(), po.getLatitude(), Boolean.TRUE.equals(po.getPrimaryAddress()));
    }

    private CustomerFieldValue toFieldValue(
            CustomerFieldValuePO po, Map<Long, FieldDefinition> fieldsById) {
        FieldDefinition field = fieldsById.get(po.getFieldId());
        if (field == null) {
            throw new IllegalStateException("Stored customer field definition is missing");
        }
        try {
            List<String> values = objectMapper.readValue(
                    po.getValuesJson(), new TypeReference<List<String>>() { });
            return new CustomerFieldValue(field.getCode(), values);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored customer field value is invalid", exception);
        }
    }
}
