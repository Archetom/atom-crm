package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.OaRequest;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaFormId;
import io.github.archetom.crm.domain.valueobject.OaRequestId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.OaRequestFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.OaRequestPO;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OaRequestPOConverter {

    private final ObjectMapper objectMapper;

    public OaRequestPOConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OaRequestPO toPO(OaRequest request) {
        OaRequestPO po = new OaRequestPO();
        po.setId(request.getId() == null ? null : request.getId().getValue());
        po.setTenantId(request.getTenantId().getValue());
        po.setFormId(request.getFormId().getValue());
        po.setFormVersion(request.getFormVersion());
        po.setFormCode(request.getFormCode());
        po.setRequestTitle(request.getRequestTitle());
        po.setApplicantActorId(request.getApplicantMemberId().getValue());
        po.setApplicantDepartmentId(request.getApplicantDepartmentId() == null ? null
                : request.getApplicantDepartmentId().getValue());
        po.setApprovalInstanceId(request.getApprovalInstanceId() == null ? null
                : request.getApprovalInstanceId().getValue());
        po.setObjectType("OA_REQUEST");
        po.setIdempotencyKey(request.getIdempotencyKey());
        po.setSubmissionDigest(request.getSubmissionDigest());
        po.setSubmittedTime(request.getSubmittedTime());
        po.setVersion(request.getVersion());
        po.setCreatedTime(request.getCreatedTime());
        po.setUpdatedTime(request.getUpdatedTime());
        return po;
    }

    public OaRequest toDomain(
            OaRequestPO root, List<OaRequestFieldValuePO> values,
            Map<Long, FieldDefinition> fieldsById) {
        if (root == null || !"OA_REQUEST".equals(root.getObjectType())) {
            throw new IllegalStateException("Stored OA request root is invalid");
        }
        LinkedHashMap<FieldCode, List<String>> facts = new LinkedHashMap<>();
        for (OaRequestFieldValuePO value : values) {
            FieldDefinition field = fieldsById.get(value.getFieldId());
            if (field == null) {
                throw new IllegalStateException("Stored OA request field is missing");
            }
            try {
                List<String> decoded = objectMapper.readValue(value.getValuesJson(),
                        new TypeReference<List<String>>() { });
                facts.put(field.getCode(), decoded);
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "Stored OA request field value is invalid", exception);
            }
        }
        return OaRequest.reconstitute(new OaRequest.Snapshot(
                new OaRequestId(root.getId()), new TenantId(root.getTenantId()),
                new OaFormId(root.getFormId()), root.getFormVersion(), root.getFormCode(),
                new MemberId(root.getApplicantActorId()),
                root.getApplicantDepartmentId() == null ? null
                        : new DepartmentId(root.getApplicantDepartmentId()),
                root.getApprovalInstanceId() == null ? null
                        : new ApprovalInstanceId(root.getApprovalInstanceId()),
                root.getIdempotencyKey(), root.getSubmissionDigest(), facts,
                root.getVersion(), root.getSubmittedTime(),
                root.getCreatedTime(), root.getUpdatedTime()));
    }

    public OaRequestFieldValuePO toValuePO(
            TenantId tenantId, OaRequestId requestId,
            FieldDefinition field, List<String> values) {
        try {
            OaRequestFieldValuePO po = new OaRequestFieldValuePO();
            po.setTenantId(tenantId.getValue());
            po.setOaRequestId(requestId.getValue());
            po.setObjectType("OA_REQUEST");
            po.setFieldId(field.getId().getValue());
            po.setValuesJson(objectMapper.writeValueAsString(values));
            po.setScalarValue(values.size() == 1 ? values.getFirst() : null);
            po.setCreatedTime(java.time.LocalDateTime.now());
            return po;
        } catch (Exception exception) {
            throw new IllegalStateException("OA request field value cannot be serialized", exception);
        }
    }
}
