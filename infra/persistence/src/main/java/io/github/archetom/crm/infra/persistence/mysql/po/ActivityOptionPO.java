package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_activity_option")
public class ActivityOptionPO extends BasePO {
    private String optionType;
    private String optionValue;
    private String label;
    private Integer displayOrder;
}
