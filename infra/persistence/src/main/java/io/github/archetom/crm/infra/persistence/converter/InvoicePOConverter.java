package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.Invoice;
import io.github.archetom.crm.domain.entity.InvoiceFieldValue;
import io.github.archetom.crm.domain.model.InvoiceStatus;
import io.github.archetom.crm.domain.model.InvoiceTitleType;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.InvoiceId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.InvoiceFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.InvoicePO;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class InvoicePOConverter {
    private final ObjectMapper objectMapper;

    public InvoicePOConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public InvoicePO toPO(Invoice invoice) {
        InvoicePO po = new InvoicePO();
        po.setId(invoice.getId() == null ? null : invoice.getId().getValue());
        po.setTenantId(invoice.getTenantId().getValue());
        po.setOwnerActorId(invoice.getOwnerMemberId().getValue());
        po.setCustomerId(invoice.getCustomerId().getValue());
        po.setContractId(invoice.getContractId().getValue());
        po.setApplicationNumber(invoice.getApplicationNumber());
        po.setAmount(invoice.getAmount());
        po.setRequestedDate(invoice.getRequestedDate());
        po.setInvoiceType(invoice.getInvoiceType());
        po.setTitleType(invoice.getTitleType().name());
        po.setTitle(invoice.getTitle());
        po.setTaxNumber(invoice.getTaxNumber());
        po.setBankName(invoice.getBankName());
        po.setBankAccount(invoice.getBankAccount());
        po.setRegisteredAddress(invoice.getRegisteredAddress());
        po.setRegisteredPhone(invoice.getRegisteredPhone());
        po.setContactName(invoice.getContactName());
        po.setContactPhone(invoice.getContactPhone());
        po.setShippingAddress(invoice.getShippingAddress());
        po.setRemark(invoice.getRemark());
        po.setStatus(invoice.getStatus().name());
        po.setInvoiceNumber(invoice.getInvoiceNumber());
        po.setIssuedDate(invoice.getIssuedDate());
        po.setLogisticsNumber(invoice.getLogisticsNumber());
        po.setVersion(invoice.getVersion());
        po.setCreatedTime(invoice.getCreatedTime());
        po.setUpdatedTime(invoice.getUpdatedTime());
        return po;
    }

    public Invoice toDomain(
            InvoicePO po, List<InvoiceFieldValuePO> values,
            Map<Long, FieldDefinition> fieldsById) {
        return Invoice.reconstitute(new Invoice.Snapshot(
                new InvoiceId(po.getId()), new TenantId(po.getTenantId()),
                new MemberId(po.getOwnerActorId()), new CustomerId(po.getCustomerId()),
                new ContractId(po.getContractId()), po.getApplicationNumber(), po.getAmount(),
                po.getRequestedDate(), po.getInvoiceType(),
                InvoiceTitleType.valueOf(po.getTitleType()), po.getTitle(), po.getTaxNumber(),
                po.getBankName(), po.getBankAccount(), po.getRegisteredAddress(),
                po.getRegisteredPhone(), po.getContactName(), po.getContactPhone(),
                po.getShippingAddress(), po.getRemark(), InvoiceStatus.valueOf(po.getStatus()),
                po.getInvoiceNumber(), po.getIssuedDate(), po.getLogisticsNumber(),
                values.stream().map(value -> toFieldValue(value, fieldsById)).toList(),
                po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
    }

    public InvoiceFieldValuePO toPO(
            TenantId tenantId, InvoiceId invoiceId,
            InvoiceFieldValue value, FieldDefinition field) {
        try {
            InvoiceFieldValuePO po = new InvoiceFieldValuePO();
            po.setTenantId(tenantId.getValue());
            po.setInvoiceId(invoiceId.getValue());
            po.setObjectType(field.getObjectType().name());
            po.setFieldId(field.getId().getValue());
            po.setValuesJson(objectMapper.writeValueAsString(value.values()));
            po.setScalarValue(value.values().size() == 1 ? value.values().getFirst() : null);
            LocalDateTime now = LocalDateTime.now();
            po.setCreatedTime(now);
            po.setUpdatedTime(now);
            return po;
        } catch (Exception exception) {
            throw new IllegalStateException("Invoice field value cannot be serialized", exception);
        }
    }

    private InvoiceFieldValue toFieldValue(
            InvoiceFieldValuePO po, Map<Long, FieldDefinition> fieldsById) {
        FieldDefinition field = fieldsById.get(po.getFieldId());
        if (field == null) {
            throw new IllegalStateException("Stored invoice field definition is missing");
        }
        try {
            return new InvoiceFieldValue(field.getCode(), objectMapper.readValue(
                    po.getValuesJson(), new TypeReference<List<String>>() { }));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored invoice field value is invalid", exception);
        }
    }
}
