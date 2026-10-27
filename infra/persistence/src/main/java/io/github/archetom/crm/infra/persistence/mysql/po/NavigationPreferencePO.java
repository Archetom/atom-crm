package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Relational representation of bounded navigation code preferences. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("navigation_preference")
public class NavigationPreferencePO extends BasePO {

    private Long actorId;
    private String orderedCodes;
    private String hiddenCodes;
}
