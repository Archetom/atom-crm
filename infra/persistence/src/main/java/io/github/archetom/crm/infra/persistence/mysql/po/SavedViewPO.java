package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("saved_view")
public class SavedViewPO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long actorId;
    private String objectType;
    private String name;
    private String conditionsJson;
    private Integer sortOrder;
    @TableField("is_hidden")
    private Boolean hidden;
    @TableField("is_default")
    private Boolean defaultView;
}
