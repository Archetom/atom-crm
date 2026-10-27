package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.StageWorkflowDefinition;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.StageDefinition;
import io.github.archetom.crm.domain.model.StageTaskDefinition;
import io.github.archetom.crm.domain.model.StageWorkflowStatus;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.StageWorkflowDefinitionPO;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class StageWorkflowDefinitionPOConverter {

    private final ObjectMapper objectMapper;

    public StageWorkflowDefinitionPOConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public StageWorkflowDefinitionPO toPO(StageWorkflowDefinition workflow) {
        if (workflow == null) {
            return null;
        }
        try {
            StageWorkflowDefinitionPO po = new StageWorkflowDefinitionPO();
            po.setId(workflow.getId() == null ? null : workflow.getId().getValue());
            po.setTenantId(workflow.getTenantId().getValue());
            po.setObjectType(workflow.getObjectType().name());
            po.setName(workflow.getName());
            po.setNormalizedName(workflow.getNormalizedName());
            po.setSuccessName(workflow.getSuccessName());
            po.setFailureName(workflow.getFailureName());
            po.setStatus(workflow.getStatus().name());
            po.setConfigJson(objectMapper.writeValueAsString(new StoredConfig(
                    workflow.getApplicableMemberIds().stream().map(MemberId::getValue).toList(),
                    workflow.getApplicableDepartmentIds().stream().map(DepartmentId::getValue).toList(),
                    workflow.getStages().stream().map(stage -> new StoredStage(
                            stage.code(), stage.name(), stage.order(), stage.probability(),
                            stage.requiredFieldCodes().stream().map(FieldCode::getValue).toList(),
                            stage.tasks(), stage.approvalDefinitionId() == null ? null
                            : stage.approvalDefinitionId().getValue())).toList())));
            po.setVersion(workflow.getVersion());
            po.setCreatedTime(workflow.getCreatedTime());
            po.setUpdatedTime(workflow.getUpdatedTime());
            return po;
        } catch (Exception exception) {
            throw new IllegalStateException("Stage workflow cannot be serialized", exception);
        }
    }

    public StageWorkflowDefinition toDomain(StageWorkflowDefinitionPO po) {
        if (po == null) {
            return null;
        }
        try {
            StoredConfig config = objectMapper.readValue(po.getConfigJson(), StoredConfig.class);
            return StageWorkflowDefinition.reconstitute(new StageWorkflowDefinition.Snapshot(
                    new StageWorkflowId(po.getId()), new TenantId(po.getTenantId()),
                    BusinessObjectType.valueOf(po.getObjectType()), po.getName(),
                    po.getSuccessName(), po.getFailureName(),
                    StageWorkflowStatus.valueOf(po.getStatus()),
                    config.applicableMemberIds().stream().map(MemberId::new).collect(Collectors.toSet()),
                    config.applicableDepartmentIds().stream().map(DepartmentId::new).collect(Collectors.toSet()),
                    config.stages().stream().map(stage -> new StageDefinition(
                            stage.code(), stage.name(), stage.order(), stage.probability(),
                            stage.requiredFieldCodes().stream().map(FieldCode::new).toList(),
                            stage.tasks(), stage.approvalDefinitionId() == null ? null
                            : new ApprovalDefinitionId(stage.approvalDefinitionId()))).toList(),
                    po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored stage workflow is invalid", exception);
        }
    }

    private record StoredConfig(
            List<Long> applicableMemberIds,
            List<Long> applicableDepartmentIds,
            List<StoredStage> stages) {
    }

    private record StoredStage(
            String code,
            String name,
            int order,
            int probability,
            List<String> requiredFieldCodes,
            List<StageTaskDefinition> tasks,
            Long approvalDefinitionId) {
    }
}
