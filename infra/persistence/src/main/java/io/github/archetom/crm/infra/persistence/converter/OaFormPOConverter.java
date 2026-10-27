package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.OaFormDefinition;
import io.github.archetom.crm.domain.entity.OaFormField;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.OaFormStatus;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaFormId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.OaFormFieldPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OaFormPO;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
public class OaFormPOConverter {

    public OaFormDefinition toDomain(
            OaFormPO po, Collection<OaFormFieldPO> fieldPOs) {
        if (po == null) {
            return null;
        }
        List<OaFormField> fields = fieldPOs == null ? List.of()
                : fieldPOs.stream().map(this::toDomain).toList();
        return OaFormDefinition.reconstitute(new OaFormDefinition.Snapshot(
                new OaFormId(po.getId()), new TenantId(po.getTenantId()),
                new MemberId(po.getCreatedByActorId()), po.getCode(), po.getName(),
                po.getDescription(), new ApprovalDefinitionId(po.getApprovalDefinitionId()),
                OaFormStatus.valueOf(po.getStatus()), fields, po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime()));
    }

    public OaFormPO toPO(OaFormDefinition form) {
        if (form == null) {
            return null;
        }
        OaFormPO po = new OaFormPO();
        po.setId(form.getId() == null ? null : form.getId().getValue());
        po.setTenantId(form.getTenantId().getValue());
        po.setCreatedByActorId(form.getCreatedByMemberId().getValue());
        po.setCode(form.getCode());
        po.setNormalizedCode(form.getNormalizedCode());
        po.setName(form.getName());
        po.setDescription(form.getDescription());
        po.setApprovalDefinitionId(form.getApprovalDefinitionId().getValue());
        po.setStatus(form.getStatus().name());
        po.setVersion(form.getVersion());
        po.setCreatedTime(form.getCreatedTime());
        po.setUpdatedTime(form.getUpdatedTime());
        return po;
    }

    public OaFormFieldPO toPO(
            TenantId tenantId, OaFormId formId, OaFormField field) {
        OaFormFieldPO po = new OaFormFieldPO();
        po.setTenantId(tenantId.getValue());
        po.setOaFormId(formId.getValue());
        po.setObjectType(BusinessObjectType.OA_REQUEST.name());
        po.setFieldCode(field.fieldCode().getValue());
        po.setRequired(field.required());
        po.setSortOrder(field.sortOrder());
        return po;
    }

    private OaFormField toDomain(OaFormFieldPO po) {
        if (!BusinessObjectType.OA_REQUEST.name().equals(po.getObjectType())) {
            throw new IllegalStateException("OA form field object type is invalid");
        }
        return new OaFormField(new FieldCode(po.getFieldCode()),
                Boolean.TRUE.equals(po.getRequired()), po.getSortOrder());
    }
}
