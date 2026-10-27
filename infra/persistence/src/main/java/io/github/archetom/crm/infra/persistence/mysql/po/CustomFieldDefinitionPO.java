package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("custom_field_definition")
public class CustomFieldDefinitionPO extends BasePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String objectType;
    private String fieldCode;
    private String displayName;
    private String description;
    private String fieldType;
    @TableField("is_builtin")
    private Boolean builtin;
    @TableField("is_required")
    private Boolean required;
    @TableField("is_unique")
    private Boolean uniqueValue;
    @TableField("is_searchable")
    private Boolean searchable;
    private String status;
    private Integer displayOrder;
    private String sectionName;
    private String optionsJson;
}
