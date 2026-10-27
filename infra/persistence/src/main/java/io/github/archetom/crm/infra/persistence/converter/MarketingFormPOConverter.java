package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.MarketingForm;
import io.github.archetom.crm.domain.entity.MarketingFormField;
import io.github.archetom.crm.domain.model.MarketingFormStatus;
import io.github.archetom.crm.domain.model.MarketingFormTargetType;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MarketingFormId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.MarketingFormFieldPO;
import io.github.archetom.crm.infra.persistence.mysql.po.MarketingFormPO;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
public class MarketingFormPOConverter {

    public MarketingForm toDomain(
            MarketingFormPO po, Collection<MarketingFormFieldPO> fieldPOs) {
        if (po == null) {
            return null;
        }
        List<MarketingFormField> fields = fieldPOs == null ? List.of()
                : fieldPOs.stream().map(this::toDomain).toList();
        return MarketingForm.reconstitute(new MarketingForm.Snapshot(
                new MarketingFormId(po.getId()), new TenantId(po.getTenantId()),
                new MemberId(po.getCreatedByActorId()), new MemberId(po.getOwnerActorId()),
                targetType(po.getTargetType()),
                po.getPublicKey(), po.getName(), po.getTitle(), po.getDescription(),
                po.getSubmitButtonText(), po.getSuccessMessage(),
                MarketingFormStatus.valueOf(po.getStatus()), fields, po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime()));
    }

    public MarketingFormPO toPO(MarketingForm form) {
        if (form == null) {
            return null;
        }
        MarketingFormPO po = new MarketingFormPO();
        po.setId(form.getId() == null ? null : form.getId().getValue());
        po.setTenantId(form.getTenantId().getValue());
        po.setCreatedByActorId(form.getCreatedByMemberId().getValue());
        po.setOwnerActorId(form.getOwnerMemberId().getValue());
        po.setTargetType(form.getTargetType().name());
        po.setPublicKey(form.getPublicKey());
        po.setName(form.getName());
        po.setTitle(form.getTitle());
        po.setDescription(form.getDescription());
        po.setSubmitButtonText(form.getSubmitButtonText());
        po.setSuccessMessage(form.getSuccessMessage());
        po.setStatus(form.getStatus().name());
        po.setVersion(form.getVersion());
        po.setCreatedTime(form.getCreatedTime());
        po.setUpdatedTime(form.getUpdatedTime());
        return po;
    }

    public MarketingFormFieldPO toPO(
            TenantId tenantId, MarketingFormId formId, MarketingFormField field) {
        MarketingFormFieldPO po = new MarketingFormFieldPO();
        po.setTenantId(tenantId.getValue());
        po.setMarketingFormId(formId.getValue());
        po.setFieldCode(field.fieldCode().getValue());
        po.setLabel(field.label());
        po.setPlaceholder(field.placeholder());
        po.setHelpText(field.helpText());
        po.setRequired(field.required());
        po.setSortOrder(field.sortOrder());
        return po;
    }

    private MarketingFormField toDomain(MarketingFormFieldPO po) {
        return new MarketingFormField(new FieldCode(po.getFieldCode()), po.getLabel(),
                po.getPlaceholder(), po.getHelpText(), Boolean.TRUE.equals(po.getRequired()),
                po.getSortOrder());
    }

    private MarketingFormTargetType targetType(String value) {
        return value == null ? MarketingFormTargetType.LEAD
                : MarketingFormTargetType.valueOf(value);
    }
}
