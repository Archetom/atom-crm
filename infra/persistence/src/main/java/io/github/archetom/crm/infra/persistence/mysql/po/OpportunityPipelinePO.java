package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Relational root row for a stable tenant Opportunity sales pipeline. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_opportunity_pipeline")
public class OpportunityPipelinePO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private Integer displayOrder;
    private String status;
}
