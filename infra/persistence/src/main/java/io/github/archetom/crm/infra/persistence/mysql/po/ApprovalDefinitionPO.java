package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("approval_definition")
public class ApprovalDefinitionPO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String objectType;
    private String name;
    private String normalizedName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    private String status;
    private String repeatMode;
    private String configJson;
}
