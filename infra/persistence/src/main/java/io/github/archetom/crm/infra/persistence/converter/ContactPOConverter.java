package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.Contact;
import io.github.archetom.crm.domain.entity.ContactAddress;
import io.github.archetom.crm.domain.entity.ContactCollaborator;
import io.github.archetom.crm.domain.entity.ContactFieldValue;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.model.ContactCollaboratorAccess;
import io.github.archetom.crm.domain.model.ContactDealStatus;
import io.github.archetom.crm.domain.model.ContactStatus;
import io.github.archetom.crm.domain.policy.ContactTagPolicy;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactAddressPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactCollaboratorPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactTagPO;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class ContactPOConverter {

    private final ObjectMapper objectMapper;

    public ContactPOConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ContactPO toPO(Contact contact) {
        if (contact == null) {
            return null;
        }
        ContactPO po = new ContactPO();
        po.setId(contact.getId() == null ? null : contact.getId().getValue());
        po.setTenantId(contact.getTenantId().getValue());
        po.setOwnerActorId(contact.getOwnerMemberId() == null
                ? null : contact.getOwnerMemberId().getValue());
        po.setCustomerId(contact.getCustomerId() == null
                ? null : contact.getCustomerId().getValue());
        po.setParentContactId(contact.getParentContactId() == null
                ? null : contact.getParentContactId().getValue());
        po.setName(contact.getName());
        po.setPhoneNumber(contact.getPhoneNumber());
        po.setTelephone(contact.getTelephone());
        po.setJobTitle(contact.getJobTitle());
        po.setEmail(contact.getEmail());
        po.setWebsite(contact.getWebsite());
        po.setDescription(contact.getDescription());
        po.setNextContactTime(contact.getNextContactTime());
        po.setStatus(contact.getStatus().name());
        po.setLocked(contact.isLocked());
        po.setDealStatus(contact.getDealStatus().name());
        po.setDealTime(contact.getDealTime());
        po.setVersion(contact.getVersion());
        po.setCreatedTime(contact.getCreatedTime());
        po.setUpdatedTime(contact.getUpdatedTime());
        return po;
    }

    public Contact toDomain(
            ContactPO po,
            List<ContactAddressPO> addresses,
            List<ContactTagPO> tags,
            List<ContactCollaboratorPO> collaborators,
            List<ContactFieldValuePO> fieldValues,
            Map<Long, FieldDefinition> fieldsById) {
        if (po == null) {
            return null;
        }
        return Contact.reconstitute(new Contact.Snapshot(
                new ContactId(po.getId()), new TenantId(po.getTenantId()),
                po.getOwnerActorId() == null ? null : new MemberId(po.getOwnerActorId()),
                po.getCustomerId() == null ? null : new CustomerId(po.getCustomerId()),
                po.getParentContactId() == null ? null : new ContactId(po.getParentContactId()),
                po.getName(), po.getPhoneNumber(), po.getTelephone(), po.getJobTitle(),
                po.getEmail(), po.getWebsite(), po.getDescription(), po.getNextContactTime(),
                ContactStatus.valueOf(po.getStatus()),
                Boolean.TRUE.equals(po.getLocked()), ContactDealStatus.valueOf(po.getDealStatus()),
                po.getDealTime(), tags.stream().map(ContactTagPO::getTagName).toList(),
                addresses.stream().map(this::toAddress).toList(),
                collaborators.stream().map(value -> new ContactCollaborator(
                        new MemberId(value.getActorId()),
                        ContactCollaboratorAccess.valueOf(value.getAccessLevel()))).toList(),
                fieldValues.stream().map(value -> toFieldValue(value, fieldsById)).toList(),
                po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
    }

    public ContactAddressPO toPO(
            TenantId tenantId, ContactId contactId, ContactAddress address) {
        ContactAddressPO po = new ContactAddressPO();
        po.setTenantId(tenantId.getValue());
        po.setContactId(contactId.getValue());
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

    public ContactTagPO toPO(TenantId tenantId, ContactId contactId, String tag) {
        ContactTagPO po = new ContactTagPO();
        po.setTenantId(tenantId.getValue());
        po.setContactId(contactId.getValue());
        po.setTagName(tag);
        po.setTagKey(ContactTagPolicy.key(tag));
        po.setCreatedTime(LocalDateTime.now());
        return po;
    }

    public ContactCollaboratorPO toPO(
            TenantId tenantId, ContactId contactId, ContactCollaborator collaborator) {
        ContactCollaboratorPO po = new ContactCollaboratorPO();
        po.setTenantId(tenantId.getValue());
        po.setContactId(contactId.getValue());
        po.setActorId(collaborator.memberId().getValue());
        po.setAccessLevel(collaborator.access().name());
        LocalDateTime now = LocalDateTime.now();
        po.setCreatedTime(now);
        po.setUpdatedTime(now);
        return po;
    }

    public ContactFieldValuePO toPO(
            TenantId tenantId, ContactId contactId,
            ContactFieldValue value, FieldDefinition field) {
        try {
            ContactFieldValuePO po = new ContactFieldValuePO();
            po.setTenantId(tenantId.getValue());
            po.setContactId(contactId.getValue());
            po.setObjectType(field.getObjectType().name());
            po.setFieldId(field.getId().getValue());
            po.setValuesJson(objectMapper.writeValueAsString(value.values()));
            po.setScalarValue(value.values().size() == 1 ? value.values().getFirst() : null);
            LocalDateTime now = LocalDateTime.now();
            po.setCreatedTime(now);
            po.setUpdatedTime(now);
            return po;
        } catch (Exception exception) {
            throw new IllegalStateException("Contact field value cannot be serialized", exception);
        }
    }

    private ContactAddress toAddress(ContactAddressPO po) {
        return new ContactAddress(po.getAddressKey(), po.getLabel(), po.getCountry(),
                po.getRegion(), po.getCity(), po.getPostalCode(), po.getAddressLine(),
                po.getLongitude(), po.getLatitude(), Boolean.TRUE.equals(po.getPrimaryAddress()));
    }

    private ContactFieldValue toFieldValue(
            ContactFieldValuePO po, Map<Long, FieldDefinition> fieldsById) {
        FieldDefinition field = fieldsById.get(po.getFieldId());
        if (field == null) {
            throw new IllegalStateException("Stored contact field definition is missing");
        }
        try {
            List<String> values = objectMapper.readValue(
                    po.getValuesJson(), new TypeReference<List<String>>() { });
            return new ContactFieldValue(field.getCode(), values);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored contact field value is invalid", exception);
        }
    }
}
