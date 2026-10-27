package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("field_layout_preference")
public class FieldLayoutPreferencePO extends BasePO {
    private Long actorId;
    private String objectType;
    private String orderedCodes;
    private String hiddenCodes;
    private String pinnedCodes;
    private String columnWidths;
}
