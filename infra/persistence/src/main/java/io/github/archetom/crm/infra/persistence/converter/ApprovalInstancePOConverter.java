package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.ApprovalInstance;
import io.github.archetom.crm.domain.entity.ApprovalTask;
import io.github.archetom.crm.domain.model.ApprovalInstanceStatus;
import io.github.archetom.crm.domain.model.ApprovalInstanceSummary;
import io.github.archetom.crm.domain.model.ApprovalRepeatMode;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.ApprovalInstancePO;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ApprovalInstancePOConverter {

    private final ObjectMapper objectMapper;

    public ApprovalInstancePOConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ApprovalInstancePO toPO(ApprovalInstance instance) {
        if (instance == null) {
            return null;
        }
        try {
            ApprovalInstancePO po = new ApprovalInstancePO();
            po.setId(instance.getId() == null ? null : instance.getId().getValue());
            po.setTenantId(instance.getTenantId().getValue());
            po.setDefinitionId(instance.getDefinitionId().getValue());
            po.setDefinitionVersion(instance.getDefinitionVersion());
            po.setObjectType(instance.getObjectType().name());
            po.setSubjectId(instance.getSubjectId());
            po.setIdempotencyKey(instance.getIdempotencyKey());
            po.setTitle(instance.getTitle());
            po.setApplicantActorId(instance.getApplicantMemberId().getValue());
            po.setRepeatMode(instance.getRepeatMode().name());
            po.setStatus(instance.getStatus().name());
            po.setCurrentStepOrder(instance.getCurrentStepOrder());
            Map<String, List<String>> facts = new LinkedHashMap<>();
            instance.getFacts().forEach((code, values) -> facts.put(code.getValue(), values));
            po.setFactsJson(objectMapper.writeValueAsString(facts));
            po.setSubmittedTime(instance.getSubmittedTime());
            po.setCompletedTime(instance.getCompletedTime());
            po.setVersion(instance.getVersion());
            po.setCreatedTime(instance.getCreatedTime());
            po.setUpdatedTime(instance.getUpdatedTime());
            return po;
        } catch (Exception exception) {
            throw new IllegalStateException("Approval instance cannot be serialized", exception);
        }
    }

    public ApprovalInstance toDomain(ApprovalInstancePO po, List<ApprovalTask> tasks,
                                     Set<MemberId> managers, Set<MemberId> watchers) {
        if (po == null) {
            return null;
        }
        try {
            return ApprovalInstance.reconstitute(new ApprovalInstance.Snapshot(
                    new ApprovalInstanceId(po.getId()), new TenantId(po.getTenantId()),
                    new ApprovalDefinitionId(po.getDefinitionId()), po.getDefinitionVersion(),
                    BusinessObjectType.valueOf(po.getObjectType()), po.getSubjectId(),
                    po.getIdempotencyKey(), po.getTitle(),
                    new MemberId(po.getApplicantActorId()),
                    ApprovalRepeatMode.valueOf(po.getRepeatMode()),
                    ApprovalInstanceStatus.valueOf(po.getStatus()), po.getCurrentStepOrder(),
                    facts(po), managers, watchers, tasks, po.getVersion(), po.getSubmittedTime(),
                    po.getCompletedTime(), po.getCreatedTime(), po.getUpdatedTime()));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored approval instance is invalid", exception);
        }
    }

    public ApprovalInstanceSummary toSummary(ApprovalInstancePO po) {
        if (po == null) {
            return null;
        }
        try {
            return new ApprovalInstanceSummary(new ApprovalInstanceId(po.getId()),
                    new ApprovalDefinitionId(po.getDefinitionId()), po.getDefinitionVersion(),
                    BusinessObjectType.valueOf(po.getObjectType()), po.getSubjectId(),
                    po.getIdempotencyKey(), po.getTitle(),
                    new MemberId(po.getApplicantActorId()),
                    ApprovalInstanceStatus.valueOf(po.getStatus()), po.getCurrentStepOrder(),
                    facts(po), po.getVersion(), po.getSubmittedTime(), po.getCompletedTime(),
                    po.getUpdatedTime());
        } catch (Exception exception) {
            throw new IllegalStateException("Stored approval instance summary is invalid", exception);
        }
    }

    private Map<FieldCode, List<String>> facts(ApprovalInstancePO po) throws Exception {
        Map<String, List<String>> stored = objectMapper.readValue(
                po.getFactsJson(), new TypeReference<Map<String, List<String>>>() { });
        return stored.entrySet().stream().collect(
                Collectors.toMap(entry -> new FieldCode(entry.getKey()), Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new));
    }
}
