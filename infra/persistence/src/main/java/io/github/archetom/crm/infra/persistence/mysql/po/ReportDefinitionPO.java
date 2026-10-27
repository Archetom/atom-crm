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
@TableName("report_definition")
public class ReportDefinitionPO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long creatorMemberId;
    private String name;
    private String normalizedName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    private String objectType;
    private String timeFieldCode;
    private String granularity;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String dimensionFieldCode;
    private String filtersJson;
    private String measuresJson;
    private String status;
}
