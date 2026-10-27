package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

@Data
@TableName("crm_marketing_form_field")
public class MarketingFormFieldPO {
    private Long tenantId;
    private Long marketingFormId;
    private String fieldCode;
    private String label;
    private String placeholder;
    private String helpText;
    @TableField("is_required")
    private Boolean required;
    private Integer sortOrder;
}
