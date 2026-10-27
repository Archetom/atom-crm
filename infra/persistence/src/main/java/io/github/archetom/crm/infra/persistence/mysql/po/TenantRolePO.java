package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Relational representation of a tenant role. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("authorization_role")
public class TenantRolePO extends BasePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String status;
    private String systemKey;
}
