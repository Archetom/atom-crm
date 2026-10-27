package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.ApprovalDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.model.ApprovalDefinitionStatus;
import io.github.archetom.crm.domain.model.ApprovalFallback;
import io.github.archetom.crm.domain.model.ApprovalRepeatMode;
import io.github.archetom.crm.domain.model.ApprovalStepDefinition;
import io.github.archetom.crm.domain.model.ApprovalStepMode;
import io.github.archetom.crm.domain.model.ApprovalStepType;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.ApprovalDefinitionPO;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ApprovalDefinitionPOConverter {

    private final ObjectMapper objectMapper;

    public ApprovalDefinitionPOConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ApprovalDefinitionPO toPO(ApprovalDefinition definition) {
        if (definition == null) {
            return null;
        }
        try {
            ApprovalDefinitionPO po = new ApprovalDefinitionPO();
            po.setId(definition.getId() == null ? null : definition.getId().getValue());
            po.setTenantId(definition.getTenantId().getValue());
            po.setObjectType(definition.getObjectType().name());
            po.setName(definition.getName());
            po.setNormalizedName(definition.getNormalizedName());
            po.setDescription(definition.getDescription());
            po.setStatus(definition.getStatus().name());
            po.setRepeatMode(definition.getRepeatMode().name());
            po.setConfigJson(objectMapper.writeValueAsString(new StoredConfig(
                    ids(definition.getInitiatorMemberIds()),
                    definition.getInitiatorDepartmentIds().stream()
                            .map(DepartmentId::getValue).toList(),
                    ids(definition.getManagerMemberIds()),
                    definition.getSteps().stream().map(this::store).toList())));
            po.setVersion(definition.getVersion());
            po.setCreatedTime(definition.getCreatedTime());
            po.setUpdatedTime(definition.getUpdatedTime());
            return po;
        } catch (Exception exception) {
            throw new IllegalStateException("Approval definition cannot be serialized", exception);
        }
    }

    public ApprovalDefinition toDomain(ApprovalDefinitionPO po) {
        if (po == null) {
            return null;
        }
        try {
            StoredConfig config = objectMapper.readValue(po.getConfigJson(), StoredConfig.class);
            return ApprovalDefinition.reconstitute(new ApprovalDefinition.Snapshot(
                    new ApprovalDefinitionId(po.getId()), new TenantId(po.getTenantId()),
                    BusinessObjectType.valueOf(po.getObjectType()), po.getName(),
                    po.getDescription(), ApprovalDefinitionStatus.valueOf(po.getStatus()),
                    ApprovalRepeatMode.valueOf(po.getRepeatMode()),
                    members(config.initiatorMemberIds()),
                    config.initiatorDepartmentIds().stream().map(DepartmentId::new)
                            .collect(Collectors.toSet()),
                    members(config.managerMemberIds()),
                    config.steps().stream().map(this::restore).toList(),
                    po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored approval definition is invalid", exception);
        }
    }

    private StoredStep store(ApprovalStepDefinition step) {
        return new StoredStep(step.code(), step.name(), step.order(), step.type().name(),
                step.mode().name(), step.fallback().name(), ids(step.memberIds()),
                step.roleIds().stream().map(RoleId::getValue).toList(),
                step.conditions().stream().map(condition -> new StoredCondition(
                        condition.fieldCode().getValue(), condition.operator().name(),
                        condition.values())).toList());
    }

    private ApprovalStepDefinition restore(StoredStep step) {
        return new ApprovalStepDefinition(step.code(), step.name(), step.order(),
                ApprovalStepType.valueOf(step.type()), ApprovalStepMode.valueOf(step.mode()),
                ApprovalFallback.valueOf(step.fallback()),
                step.memberIds().stream().map(MemberId::new).toList(),
                step.roleIds().stream().map(RoleId::new).toList(),
                step.conditions().stream().map(condition -> new FilterCondition(
                        new FieldCode(condition.fieldCode()),
                        FilterOperator.valueOf(condition.operator()),
                        condition.values())).toList());
    }

    private List<Long> ids(Iterable<MemberId> values) {
        java.util.ArrayList<Long> result = new java.util.ArrayList<>();
        values.forEach(value -> result.add(value.getValue()));
        return List.copyOf(result);
    }

    private Set<MemberId> members(List<Long> values) {
        return values.stream().map(MemberId::new).collect(Collectors.toSet());
    }

    private record StoredConfig(
            List<Long> initiatorMemberIds,
            List<Long> initiatorDepartmentIds,
            List<Long> managerMemberIds,
            List<StoredStep> steps) {
    }

    private record StoredStep(
            String code,
            String name,
            int order,
            String type,
            String mode,
            String fallback,
            List<Long> memberIds,
            List<Long> roleIds,
            List<StoredCondition> conditions) {
    }

    private record StoredCondition(String fieldCode, String operator, List<String> values) {
    }
}
