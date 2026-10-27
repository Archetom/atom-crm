package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("stage_workflow_definition")
public class StageWorkflowDefinitionPO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String objectType;
    private String name;
    private String normalizedName;
    private String successName;
    private String failureName;
    private String status;
    private String configJson;
}
