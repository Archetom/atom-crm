package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.Lead;
import io.github.archetom.crm.domain.entity.LeadAddress;
import io.github.archetom.crm.domain.entity.LeadCollaborator;
import io.github.archetom.crm.domain.entity.LeadFieldValue;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.model.LeadCollaboratorAccess;
import io.github.archetom.crm.domain.model.LeadDealStatus;
import io.github.archetom.crm.domain.model.LeadStatus;
import io.github.archetom.crm.domain.policy.LeadTagPolicy;
import io.github.archetom.crm.domain.valueobject.LeadId;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadAddressPO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadCollaboratorPO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadPO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadTagPO;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class LeadPOConverter {

    private final ObjectMapper objectMapper;

    public LeadPOConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LeadPO toPO(Lead lead) {
        if (lead == null) {
            return null;
        }
        LeadPO po = new LeadPO();
        po.setId(lead.getId() == null ? null : lead.getId().getValue());
        po.setTenantId(lead.getTenantId().getValue());
        po.setOwnerActorId(lead.getOwnerMemberId() == null
                ? null : lead.getOwnerMemberId().getValue());
        po.setCustomerId(lead.getCustomerId() == null
                ? null : lead.getCustomerId().getValue());
        po.setConvertedContactId(lead.getConvertedContactId() == null
                ? null : lead.getConvertedContactId().getValue());
        po.setParentLeadId(lead.getParentLeadId() == null
                ? null : lead.getParentLeadId().getValue());
        po.setName(lead.getName());
        po.setPhoneNumber(lead.getPhoneNumber());
        po.setTelephone(lead.getTelephone());
        po.setJobTitle(lead.getJobTitle());
        po.setEmail(lead.getEmail());
        po.setWebsite(lead.getWebsite());
        po.setDescription(lead.getDescription());
        po.setNextContactTime(lead.getNextContactTime());
        po.setStatus(lead.getStatus().name());
        po.setLocked(lead.isLocked());
        po.setDealStatus(lead.getDealStatus().name());
        po.setDealTime(lead.getDealTime());
        po.setVersion(lead.getVersion());
        po.setCreatedTime(lead.getCreatedTime());
        po.setUpdatedTime(lead.getUpdatedTime());
        return po;
    }

    public Lead toDomain(
            LeadPO po,
            List<LeadAddressPO> addresses,
            List<LeadTagPO> tags,
            List<LeadCollaboratorPO> collaborators,
            List<LeadFieldValuePO> fieldValues,
            Map<Long, FieldDefinition> fieldsById) {
        if (po == null) {
            return null;
        }
        return Lead.reconstitute(new Lead.Snapshot(
                new LeadId(po.getId()), new TenantId(po.getTenantId()),
                po.getOwnerActorId() == null ? null : new MemberId(po.getOwnerActorId()),
                po.getCustomerId() == null ? null : new CustomerId(po.getCustomerId()),
                po.getConvertedContactId() == null ? null : new ContactId(po.getConvertedContactId()),
                po.getParentLeadId() == null ? null : new LeadId(po.getParentLeadId()),
                po.getName(), po.getPhoneNumber(), po.getTelephone(), po.getJobTitle(),
                po.getEmail(), po.getWebsite(), po.getDescription(), po.getNextContactTime(),
                LeadStatus.valueOf(po.getStatus()),
                Boolean.TRUE.equals(po.getLocked()), LeadDealStatus.valueOf(po.getDealStatus()),
                po.getDealTime(), tags.stream().map(LeadTagPO::getTagName).toList(),
                addresses.stream().map(this::toAddress).toList(),
                collaborators.stream().map(value -> new LeadCollaborator(
                        new MemberId(value.getActorId()),
                        LeadCollaboratorAccess.valueOf(value.getAccessLevel()))).toList(),
                fieldValues.stream().map(value -> toFieldValue(value, fieldsById)).toList(),
                po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
    }

    public LeadAddressPO toPO(
            TenantId tenantId, LeadId leadId, LeadAddress address) {
        LeadAddressPO po = new LeadAddressPO();
        po.setTenantId(tenantId.getValue());
        po.setLeadId(leadId.getValue());
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

    public LeadTagPO toPO(TenantId tenantId, LeadId leadId, String tag) {
        LeadTagPO po = new LeadTagPO();
        po.setTenantId(tenantId.getValue());
        po.setLeadId(leadId.getValue());
        po.setTagName(tag);
        po.setTagKey(LeadTagPolicy.key(tag));
        po.setCreatedTime(LocalDateTime.now());
        return po;
    }

    public LeadCollaboratorPO toPO(
            TenantId tenantId, LeadId leadId, LeadCollaborator collaborator) {
        LeadCollaboratorPO po = new LeadCollaboratorPO();
        po.setTenantId(tenantId.getValue());
        po.setLeadId(leadId.getValue());
        po.setActorId(collaborator.memberId().getValue());
        po.setAccessLevel(collaborator.access().name());
        LocalDateTime now = LocalDateTime.now();
        po.setCreatedTime(now);
        po.setUpdatedTime(now);
        return po;
    }

    public LeadFieldValuePO toPO(
            TenantId tenantId, LeadId leadId,
            LeadFieldValue value, FieldDefinition field) {
        try {
            LeadFieldValuePO po = new LeadFieldValuePO();
            po.setTenantId(tenantId.getValue());
            po.setLeadId(leadId.getValue());
            po.setObjectType(field.getObjectType().name());
            po.setFieldId(field.getId().getValue());
            po.setValuesJson(objectMapper.writeValueAsString(value.values()));
            po.setScalarValue(value.values().size() == 1 ? value.values().getFirst() : null);
            LocalDateTime now = LocalDateTime.now();
            po.setCreatedTime(now);
            po.setUpdatedTime(now);
            return po;
        } catch (Exception exception) {
            throw new IllegalStateException("Lead field value cannot be serialized", exception);
        }
    }

    private LeadAddress toAddress(LeadAddressPO po) {
        return new LeadAddress(po.getAddressKey(), po.getLabel(), po.getCountry(),
                po.getRegion(), po.getCity(), po.getPostalCode(), po.getAddressLine(),
                po.getLongitude(), po.getLatitude(), Boolean.TRUE.equals(po.getPrimaryAddress()));
    }

    private LeadFieldValue toFieldValue(
            LeadFieldValuePO po, Map<Long, FieldDefinition> fieldsById) {
        FieldDefinition field = fieldsById.get(po.getFieldId());
        if (field == null) {
            throw new IllegalStateException("Stored lead field definition is missing");
        }
        try {
            List<String> values = objectMapper.readValue(
                    po.getValuesJson(), new TypeReference<List<String>>() { });
            return new LeadFieldValue(field.getCode(), values);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored lead field value is invalid", exception);
        }
    }
}
