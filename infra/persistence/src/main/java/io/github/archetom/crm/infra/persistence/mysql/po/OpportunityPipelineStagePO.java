package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Relational root row for a stable tenant Opportunity sales stage under a pipeline code. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_opportunity_pipeline_stage")
public class OpportunityPipelineStagePO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String pipelineCode;
    private String code;
    private String name;
    private Integer displayOrder;
    private Integer probability;
    private String status;
}
