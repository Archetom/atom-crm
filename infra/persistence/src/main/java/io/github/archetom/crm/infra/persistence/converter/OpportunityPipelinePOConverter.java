package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.OpportunityPipeline;
import io.github.archetom.crm.domain.entity.OpportunityPipelineStage;
import io.github.archetom.crm.domain.model.OpportunityPipelineStatus;
import io.github.archetom.crm.domain.valueobject.OpportunityPipelineId;
import io.github.archetom.crm.domain.valueobject.OpportunityPipelineStageId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityPipelinePO;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityPipelineStagePO;
import org.springframework.stereotype.Component;

@Component
public class OpportunityPipelinePOConverter {

    public OpportunityPipelinePO toPO(OpportunityPipeline pipeline) {
        OpportunityPipelinePO po = new OpportunityPipelinePO();
        po.setId(pipeline.getId() == null ? null : pipeline.getId().getValue());
        po.setTenantId(pipeline.getTenantId().getValue());
        po.setCode(pipeline.getCode());
        po.setName(pipeline.getName());
        po.setDisplayOrder(pipeline.getDisplayOrder());
        po.setStatus(pipeline.getStatus().name());
        po.setVersion(pipeline.getVersion());
        po.setCreatedTime(pipeline.getCreatedTime());
        po.setUpdatedTime(pipeline.getUpdatedTime());
        return po;
    }

    public OpportunityPipelineStagePO toPO(OpportunityPipelineStage stage) {
        OpportunityPipelineStagePO po = new OpportunityPipelineStagePO();
        po.setId(stage.getId() == null ? null : stage.getId().getValue());
        po.setTenantId(stage.getTenantId().getValue());
        po.setPipelineCode(stage.getPipelineCode());
        po.setCode(stage.getCode());
        po.setName(stage.getName());
        po.setDisplayOrder(stage.getDisplayOrder());
        po.setProbability(stage.getProbability());
        po.setStatus(stage.getStatus().name());
        po.setVersion(stage.getVersion());
        po.setCreatedTime(stage.getCreatedTime());
        po.setUpdatedTime(stage.getUpdatedTime());
        return po;
    }

    public OpportunityPipeline toDomain(OpportunityPipelinePO po) {
        if (po == null) {
            return null;
        }
        return OpportunityPipeline.reconstitute(new OpportunityPipeline.Snapshot(
                new OpportunityPipelineId(po.getId()), new TenantId(po.getTenantId()),
                po.getCode(), po.getName(), po.getDisplayOrder(),
                OpportunityPipelineStatus.valueOf(po.getStatus()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime()));
    }

    public OpportunityPipelineStage toDomain(OpportunityPipelineStagePO po) {
        if (po == null) {
            return null;
        }
        return OpportunityPipelineStage.reconstitute(new OpportunityPipelineStage.Snapshot(
                new OpportunityPipelineStageId(po.getId()), new TenantId(po.getTenantId()),
                po.getPipelineCode(), po.getCode(), po.getName(), po.getDisplayOrder(),
                po.getProbability(), OpportunityPipelineStatus.valueOf(po.getStatus()),
                po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
    }
}
