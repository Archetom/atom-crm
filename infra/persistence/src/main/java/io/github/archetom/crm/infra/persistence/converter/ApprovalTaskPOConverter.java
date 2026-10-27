package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.ApprovalTask;
import io.github.archetom.crm.domain.model.ApprovalStepMode;
import io.github.archetom.crm.domain.model.ApprovalTaskStatus;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.ApprovalTaskPO;
import org.springframework.stereotype.Component;

@Component
public class ApprovalTaskPOConverter {

    public ApprovalTaskPO toPO(TenantId tenantId, Long instanceId, ApprovalTask task) {
        if (tenantId == null || instanceId == null || task == null) {
            throw new IllegalArgumentException("approval task persistence key is required");
        }
        ApprovalTaskPO po = new ApprovalTaskPO();
        po.setTenantId(tenantId.getValue());
        po.setTaskKey(task.getTaskKey());
        po.setInstanceId(instanceId);
        po.setSourceTaskKey(task.getSourceTaskKey());
        po.setStepCode(task.getStepCode());
        po.setStepName(task.getStepName());
        po.setStepOrder(task.getStepOrder());
        po.setMode(task.getMode().name());
        po.setActorOrder(task.getActorOrder());
        po.setAssignedActorId(task.getAssignedMemberId().getValue());
        po.setStatus(task.getStatus().name());
        po.setRemark(task.getRemark());
        po.setActedTime(task.getActedTime());
        po.setVersion(task.getVersion());
        po.setCreatedTime(task.getCreatedTime());
        po.setUpdatedTime(task.getUpdatedTime());
        return po;
    }

    public ApprovalTask toDomain(ApprovalTaskPO po) {
        if (po == null) {
            return null;
        }
        return ApprovalTask.reconstitute(new ApprovalTask.Snapshot(
                po.getTaskKey(), po.getSourceTaskKey(), po.getStepCode(), po.getStepName(),
                po.getStepOrder(), ApprovalStepMode.valueOf(po.getMode()), po.getActorOrder(),
                new MemberId(po.getAssignedActorId()), ApprovalTaskStatus.valueOf(po.getStatus()),
                po.getRemark(), po.getActedTime(), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime()));
    }
}
