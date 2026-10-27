package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("oa_form_field")
public class OaFormFieldPO {
    private Long tenantId;
    private Long oaFormId;
    private String objectType;
    private String fieldCode;
    @TableField("is_required")
    private Boolean required;
    private Integer sortOrder;
}
